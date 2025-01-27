package eu.tijlb.opengpslogger.database.location

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.provider.BaseColumns
import android.util.Log
import android.widget.Toast
import eu.tijlb.opengpslogger.database.location.migration.MigrationV8
import eu.tijlb.opengpslogger.database.location.migration.MigrationV9
import eu.tijlb.opengpslogger.database.location.util.LocationDbCreationUtil
import eu.tijlb.opengpslogger.database.location.util.LocationDbFilterUtil.Companion.getFilter
import eu.tijlb.opengpslogger.database.location.util.LocationDbRangeUtil
import eu.tijlb.opengpslogger.dto.BBoxDto
import eu.tijlb.opengpslogger.query.PointsQuery
import eu.tijlb.opengpslogger.util.OutlierUtil
import eu.tijlb.opengpslogger.util.OutlierUtil.Companion.calculateAngle
import eu.tijlb.opengpslogger.util.OutlierUtil.Companion.calculateDistance
import kotlin.math.cos

class LocationDbHelper(val context: Context) :
    SQLiteOpenHelper(context, LocationDbContract.FILE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        LocationDbCreationUtil.create(db, DATABASE_VERSION)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Toast.makeText(
            context,
            "Starting database migration. Do not close the app ($oldVersion -> $newVersion).",
            Toast.LENGTH_LONG
        ).show()
        if (oldVersion < 8) {
            MigrationV8.migrate(db)
        }
        if (oldVersion < 9) {
            MigrationV9(context).migrate(db)
        }
        Toast.makeText(context, "Done migrating database.", Toast.LENGTH_SHORT).show()
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.execSQL("PRAGMA synchronous = NORMAL")
    }

    fun save(location: Location, source: String): Long {
        val hashes = calculateHashes(location.latitude, location.longitude, location.time)

        val values = ContentValues().apply {
            val speed = if (location.hasSpeed()) location.speed else null
            val speedAccuracy =
                if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else null
            val accuracy = if (location.hasAccuracy()) location.accuracy else null

            put(LocationDbContract.COLUMN_NAME_TIMESTAMP, location.time)
            put(LocationDbContract.COLUMN_NAME_LATITUDE, location.latitude)
            put(LocationDbContract.COLUMN_NAME_LONGITUDE, location.longitude)
            put(LocationDbContract.COLUMN_NAME_SPEED, speed)
            put(LocationDbContract.COLUMN_NAME_SPEED_ACCURACY, speedAccuracy)
            put(LocationDbContract.COLUMN_NAME_ACCURACY, accuracy)
            put(LocationDbContract.COLUMN_NAME_CREATED_ON, System.currentTimeMillis())
            put(LocationDbContract.COLUMN_NAME_SOURCE, source)
            put(LocationDbContract.COLUMN_NAME_HASH_50M_1D, hashes.first)
            put(LocationDbContract.COLUMN_NAME_HASH_250M_1D, hashes.second)
        }

        val db = writableDatabase
        val newRowId = db.replace(LocationDbContract.TABLE_NAME, null, values)
        if (newRowId % 1000 == 0L) {
            Log.d("ogl-locationdbhelper", "Saved $newRowId: $values to database")
        }
        return newRowId
    }

    fun getPointsCursor(query: PointsQuery): Cursor {
        val latRange = query.bbox?.latRange() ?: 0.0

        val groupBy = latRange.let {
            when {
                it < 2 -> null
                it < 5 -> LocationDbContract.COLUMN_NAME_HASH_50M_1D
                else -> LocationDbContract.COLUMN_NAME_HASH_250M_1D
            }
        }

        val projection = arrayOf(
            LocationDbContract.COLUMN_NAME_TIMESTAMP,
            LocationDbContract.COLUMN_NAME_LATITUDE,
            LocationDbContract.COLUMN_NAME_LONGITUDE,
        )


        val db = readableDatabase
        return db.query(
            LocationDbContract.TABLE_NAME,
            projection,
            getFilter(query),
            null,
            groupBy,
            null,
            "${LocationDbContract.COLUMN_NAME_TIMESTAMP} ASC"
        )
    }

    fun getTimeRange(query: PointsQuery): Pair<Long, Long> {
        val db = readableDatabase
        return LocationDbRangeUtil.getTimeRange(db, query)
    }

    fun getCoordsRange(query: PointsQuery): BBoxDto {
        val db = this.readableDatabase
        return LocationDbRangeUtil.getCoordsRange(db, query)
    }

    fun getDataSources(): List<String> {
        val db = readableDatabase
        val query = """
            SELECT DISTINCT(${LocationDbContract.COLUMN_NAME_SOURCE}) AS source
            FROM ${LocationDbContract.TABLE_NAME}
            ORDER BY ${BaseColumns._ID} DESC
        """.trimIndent()
        val list = mutableListOf<String>()
        db.rawQuery(query, null)
            .use { cursor ->
                if (cursor.moveToFirst()) {
                    val sourceIndex = cursor.getColumnIndex("source")
                    do {
                        val source = cursor.getString(sourceIndex)
                        source?.let { list.add(it) }
                    } while (cursor.moveToNext())
                }
            }
        Log.d("ogl-locationdbhelper", "Datasource sources $list")
        return list
    }

    fun deleteData(dataSource: String) {
        val db = writableDatabase
        val whereClause = "${LocationDbContract.COLUMN_NAME_SOURCE} = ?"
        val whereArgs = arrayOf(dataSource)

        val deletedRows = db.delete(LocationDbContract.TABLE_NAME, whereClause, whereArgs)
        Log.d("ogl-locationdbhelper", "Deleted $deletedRows rows from datasource $dataSource")
    }

    fun updateDistAngleIfNeeded() {
        while (batchUpdateDistAngle() > 0) {
            Log.d("ogl-locationdbhelper", "Batch updating distance and angle in progress")
        }
    }

    @SuppressLint("Range")
    private fun batchUpdateDistAngle(): Int {
        val db = writableDatabase
        val cursor = db.rawQuery(
            """
                SELECT ${LocationDbContract.COLUMN_NAME_SOURCE}, 
                       ${LocationDbContract.COLUMN_NAME_TIMESTAMP}
                FROM ${LocationDbContract.TABLE_NAME}
                WHERE ${LocationDbContract.COLUMN_NAME_NEIGHBOR_DISTANCE} IS NULL
                    AND ${LocationDbContract.COLUMN_NAME_SOURCE} IS NOT NULL
                LIMIT 1000
                """.trimIndent(), null
        )

        val rowsToUpdate = mutableListOf<Pair<String, Long>>()

        cursor.use {
            if (cursor.moveToFirst()) {
                val columnIndexSource = it.getColumnIndex(LocationDbContract.COLUMN_NAME_SOURCE)
                val columnIndexTimestamp =
                    it.getColumnIndex(LocationDbContract.COLUMN_NAME_TIMESTAMP)
                while (it.moveToNext()) {
                    val source = it.getString(columnIndexSource)
                    val timestamp = it.getLong(columnIndexTimestamp)
                    rowsToUpdate.add(Pair(source, timestamp))
                }
            }
        }

        Log.d("ogl-locationdbhelper", "Updating distance and angle for ${rowsToUpdate.size} points")
        db.beginTransaction()
        try {
            rowsToUpdate.forEach {
                updateDistAngle(db, it.first, it.second)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Log.d(
            "ogl-locationdbhelper",
            "Done updating distance and angle for ${rowsToUpdate.size} points"
        )
        return rowsToUpdate.size
    }

    private fun updateDistAngle(db: SQLiteDatabase, source: String, timestamp: Long) {
        val last3 = findNeighbors(source, timestamp)
        if (last3.size == 3) {
            val prevPoint = last3[0]
            val middlePoint = last3[1]
            val nextPoint = last3[2]
            val distance = calculateDistance(prevPoint, middlePoint, nextPoint)
            val angle = calculateAngle(prevPoint, middlePoint, nextPoint)

            db.execSQL(
                """
                UPDATE ${LocationDbContract.TABLE_NAME}
                SET ${LocationDbContract.COLUMN_NAME_NEIGHBOR_DISTANCE} = $distance,
                    ${LocationDbContract.COLUMN_NAME_NEIGHBOR_ANGLE} = $angle
                WHERE ${BaseColumns._ID} = ${middlePoint.id}
                """
            )
        }

    }

    @SuppressLint("Range")
    private fun findNeighbors(source: String, timestamp: Long): MutableList<OutlierUtil.Point> {
        val db = readableDatabase

        val cursor = db.rawQuery(
        """
        (
            SELECT ${BaseColumns._ID}, 
                   ${LocationDbContract.COLUMN_NAME_LATITUDE},
                   ${LocationDbContract.COLUMN_NAME_LONGITUDE},
                   ${LocationDbContract.COLUMN_NAME_TIMESTAMP}
            FROM ${LocationDbContract.TABLE_NAME}
            WHERE ${LocationDbContract.COLUMN_NAME_SOURCE} = ? 
              AND ${LocationDbContract.COLUMN_NAME_TIMESTAMP} < ?
            ORDER BY ${LocationDbContract.COLUMN_NAME_TIMESTAMP} DESC
            LIMIT 1
        )
        UNION
        (
            SELECT ${BaseColumns._ID}, 
                   ${LocationDbContract.COLUMN_NAME_LATITUDE},
                   ${LocationDbContract.COLUMN_NAME_LONGITUDE},
                   ${LocationDbContract.COLUMN_NAME_TIMESTAMP}
            FROM ${LocationDbContract.TABLE_NAME}
            WHERE ${LocationDbContract.COLUMN_NAME_SOURCE} = ? 
              AND ${LocationDbContract.COLUMN_NAME_TIMESTAMP} = ?
            LIMIT 1
        )
        UNION
        (
            SELECT ${BaseColumns._ID}, 
                   ${LocationDbContract.COLUMN_NAME_LATITUDE},
                   ${LocationDbContract.COLUMN_NAME_LONGITUDE},
                   ${LocationDbContract.COLUMN_NAME_TIMESTAMP}
            FROM ${LocationDbContract.TABLE_NAME}
            WHERE ${LocationDbContract.COLUMN_NAME_SOURCE} = ? 
              AND ${LocationDbContract.COLUMN_NAME_TIMESTAMP} > ?
            ORDER BY ${LocationDbContract.COLUMN_NAME_TIMESTAMP} ASC
            LIMIT 1
        )
        """.trimIndent(),
            arrayOf(
                source,
                timestamp.toString(),
                source,
                timestamp.toString(),
                source,
                timestamp.toString()
            )
        )

        val last3Points = mutableListOf<OutlierUtil.Point>()
        cursor.use {
            while (it.moveToNext()) {
                val columnIndexId = it.getColumnIndex(BaseColumns._ID)
                val columnIndexLatitude = it.getColumnIndex(LocationDbContract.COLUMN_NAME_LATITUDE)
                val columnIndexLongitude =
                    it.getColumnIndex(LocationDbContract.COLUMN_NAME_LONGITUDE)
                val columnIndexTimestamp =
                    it.getColumnIndex(LocationDbContract.COLUMN_NAME_TIMESTAMP)

                val id = it.getLong(columnIndexId)
                val latitude = it.getDouble(columnIndexLatitude)
                val longitude = it.getDouble(columnIndexLongitude)
                val time = it.getLong(columnIndexTimestamp)

                val point = OutlierUtil.Point(
                    id = id,
                    latitude = latitude,
                    longitude = longitude,
                    timestamp = time
                )
                last3Points.add(point)
            }
        }
        return last3Points
    }

    companion object {
        private const val DATABASE_VERSION = 9 // Increment on schema change
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000

        private var instance: LocationDbHelper? = null

        fun getInstance(context: Context): LocationDbHelper {
            return instance ?: synchronized(this) {
                instance ?: LocationDbHelper(context).also { instance = it }
            }
        }

        fun calculateHashes(
            latitude: Double,
            longitude: Double,
            timestamp: Long
        ): Pair<Long, Long> {
            val dayHash = timestamp / MILLIS_PER_DAY

            val equatorCircumference = 111_320.0
            val latCircumference = equatorCircumference
            val lonCircumference = equatorCircumference * cos(Math.toRadians(latitude))

            val latPerMeter = (latitude + 90) * latCircumference
            val lonPerMeter = (longitude + 180) * lonCircumference

            val scaledLat50 = (latPerMeter / 50.0).toLong()
            val scaledLon50 = (lonPerMeter / 50.0).toLong()
            val hash1d50m = (dayHash shl 40) or (scaledLat50 shl 20) or scaledLon50

            val scaledLat250 = (latPerMeter / 250.0).toLong()
            val scaledLon250 = (lonPerMeter / 250.0).toLong()
            val hash1d250m = (dayHash shl 40) or (scaledLat250 shl 20) or scaledLon250

            return Pair(hash1d50m, hash1d250m)
        }
    }
}