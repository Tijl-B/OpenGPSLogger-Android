package eu.tijlb.opengpslogger.database.location

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.provider.BaseColumns
import android.util.Log
import eu.tijlb.opengpslogger.database.location.migration.MigrationV8
import eu.tijlb.opengpslogger.dto.BBoxDto
import eu.tijlb.opengpslogger.query.PointsQuery
import kotlin.math.cos

class LocationDbHelper(context: Context) :
    SQLiteOpenHelper(context, LocationDbContract.FILE_NAME, null, DATABASE_VERSION) {

    private val createTableSql =
        """
            CREATE TABLE IF NOT EXISTS ${LocationDbContract.TABLE_NAME}  
            (${BaseColumns._ID} INTEGER PRIMARY KEY,
            ${LocationDbContract.COLUMN_NAME_TIMESTAMP} INTEGER,
            ${LocationDbContract.COLUMN_NAME_LATITUDE} DOUBLE,
            ${LocationDbContract.COLUMN_NAME_LONGITUDE} DOUBLE,
            ${LocationDbContract.COLUMN_NAME_SPEED} DOUBLE,
            ${LocationDbContract.COLUMN_NAME_SPEED_ACCURACY} DOUBLE,
            ${LocationDbContract.COLUMN_NAME_ACCURACY} DOUBLE,
            ${LocationDbContract.COLUMN_NAME_SOURCE} TEXT,
            ${LocationDbContract.COLUMN_NAME_CREATED_ON} INTEGER,
            ${LocationDbContract.COLUMN_NAME_HASH_50M_1D} LONG,
            ${LocationDbContract.COLUMN_NAME_HASH_250M_1D} LONG,
            UNIQUE(${LocationDbContract.COLUMN_NAME_TIMESTAMP}, ${LocationDbContract.COLUMN_NAME_SOURCE})
            )
        """.trimIndent()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(createTableSql)
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_timestamp 
            ON ${LocationDbContract.TABLE_NAME} (${LocationDbContract.COLUMN_NAME_TIMESTAMP})
            """
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_latitude_longitude 
            ON ${LocationDbContract.TABLE_NAME} (${LocationDbContract.COLUMN_NAME_LATITUDE}, ${LocationDbContract.COLUMN_NAME_LONGITUDE})
            """
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_data_source 
            ON ${LocationDbContract.TABLE_NAME} (${LocationDbContract.COLUMN_NAME_SOURCE})
            """
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_hash_50m_1d 
            ON ${LocationDbContract.TABLE_NAME} (${LocationDbContract.COLUMN_NAME_HASH_50M_1D})
            """
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_hash_250m_1d 
            ON ${LocationDbContract.TABLE_NAME} (${LocationDbContract.COLUMN_NAME_HASH_250M_1D})
            """
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 8) {
            MigrationV8.migrate(db)
        }
    }


    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.execSQL("PRAGMA synchronous = NORMAL")
    }

    fun save(location: Location, source: String) {
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
        val newRowId = db?.replace(LocationDbContract.TABLE_NAME, null, values)
        if ((newRowId ?: 1L) % 1000 == 0L) {
            Log.d("ogl-locationdbhelper", "Saved $newRowId: $values to database")
        }
    }

    fun getTimeRange(query: PointsQuery): Pair<Long, Long> {
        val db = readableDatabase
        val query = """
            SELECT MIN(${LocationDbContract.COLUMN_NAME_TIMESTAMP}) AS minTimestamp,
             MAX(${LocationDbContract.COLUMN_NAME_TIMESTAMP}) AS maxTimestamp
            FROM ${LocationDbContract.TABLE_NAME}
            WHERE ${getFilter(query)}
        """.trimIndent()
        db.rawQuery(query, null)
            .use { cursor ->
                if (cursor.moveToFirst()) {
                    val lowestTimestamp =
                        cursor.getLong(cursor.getColumnIndexOrThrow("minTimestamp"))
                    var highestTimestamp =
                        cursor.getLong(cursor.getColumnIndexOrThrow("maxTimestamp"))
                    cursor.close()
                    Log.d("ogl-locationdbhelper", "Got oldest timestamp $lowestTimestamp")
                    if (highestTimestamp == 0L) highestTimestamp = System.currentTimeMillis()
                    return Pair(lowestTimestamp, highestTimestamp)
                }
            }
        return Pair(0L, System.currentTimeMillis())
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

    fun getCoordsRange(query: PointsQuery): BBoxDto {
        val db = this.readableDatabase
        val query = """
            SELECT MIN(${LocationDbContract.COLUMN_NAME_LONGITUDE}) AS lonMin, 
                MAX(${LocationDbContract.COLUMN_NAME_LONGITUDE}) AS lonMax,
                MIN(${LocationDbContract.COLUMN_NAME_LATITUDE}) AS latMin, 
                MAX(${LocationDbContract.COLUMN_NAME_LATITUDE}) AS latMax 
            FROM ${LocationDbContract.TABLE_NAME}
            WHERE ${getFilter(query)}
        """.trimIndent()

        db.rawQuery(query, null)
            .use { cursor ->
                if (cursor.moveToFirst()) {
                    val minLat = cursor.getDouble(cursor.getColumnIndexOrThrow("latMin"))
                    val maxLat = cursor.getDouble(cursor.getColumnIndexOrThrow("latMax"))
                    val minLon = cursor.getDouble(cursor.getColumnIndexOrThrow("lonMin"))
                    val maxLon = cursor.getDouble(cursor.getColumnIndexOrThrow("lonMax"))
                    Log.d("7895", "Got min lon $minLon, max lon $maxLon")
                    if (minLon == maxLon || minLat == maxLat) {
                        return BBoxDto.defaultBbox()
                    }
                    return BBoxDto(
                        minLat = minLat,
                        maxLat = maxLat,
                        minLon = minLon,
                        maxLon = maxLon
                    )
                }
            }
        return BBoxDto.defaultBbox()
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

    private fun getFilter(query: PointsQuery): String {
        val bboxFilter =
            query.bbox?.let {
                """
            (
              ${LocationDbContract.COLUMN_NAME_LATITUDE} >= ${it.minLat} AND ${LocationDbContract.COLUMN_NAME_LATITUDE} <= ${it.maxLat}
              AND 
              ${LocationDbContract.COLUMN_NAME_LONGITUDE} >= ${it.minLon} AND ${LocationDbContract.COLUMN_NAME_LONGITUDE} <= ${it.maxLon}
            )
            AND
                """
            } ?: ""
        val accuracyFilter =
            query.minAccuracy?.let {
                """
            (
                ${LocationDbContract.COLUMN_NAME_ACCURACY} <= $it
            )
            AND
            """
            } ?: ""
        val timestampFilter =
            """
             (
                ( 
                ${LocationDbContract.COLUMN_NAME_TIMESTAMP} >= ${query.startDateMillis}
                AND ${LocationDbContract.COLUMN_NAME_TIMESTAMP} <= ${query.endDateMillis}
                ) 
              OR ${LocationDbContract.COLUMN_NAME_TIMESTAMP} IS NULL
              )
              """

        val filter = """
             $bboxFilter
             $accuracyFilter
             $timestampFilter
              ${if (query.dataSource != "All") "AND ${LocationDbContract.COLUMN_NAME_SOURCE} = '${query.dataSource}'" else ""}
            """.trimIndent()
        Log.d("ogl-locationdbhelper-filter", "Using filter $filter")
        return filter
    }

    fun deleteData(dataSource: String) {
        val db = writableDatabase
        val whereClause = "${LocationDbContract.COLUMN_NAME_SOURCE} = ?"
        val whereArgs = arrayOf(dataSource)

        val deletedRows = db.delete(LocationDbContract.TABLE_NAME, whereClause, whereArgs)
        Log.d("ogl-locationdbhelper", "Deleted $deletedRows rows from datasource $dataSource")
    }

    companion object {
        private const val DATABASE_VERSION = 8 // Increment on schema change
        private const val millisInDay = 24 * 60 * 60 * 1000

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
            val dayHash = timestamp / millisInDay

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