package eu.tijlb.opengpslogger.model.database.densitymap

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.provider.BaseColumns
import android.util.Log
import eu.tijlb.opengpslogger.model.database.densitymap.continent.DensityMapDbContract
import eu.tijlb.opengpslogger.model.database.location.LocationDbContract
import eu.tijlb.opengpslogger.model.database.location.util.LocationDbFilterUtil.getFilter
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.model.dto.query.PointsQuery
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

abstract class AbstractDensityMapDbHelper(
    context: Context,
    databaseName: String,
    databaseVersion: Int
) :
    SQLiteOpenHelper(context, databaseName, null, databaseVersion) {

    private val createTableSql =
        """
            CREATE TABLE IF NOT EXISTS ${DensityMapDbContract.TABLE_NAME}  
            (${BaseColumns._ID} INTEGER PRIMARY KEY,
            ${DensityMapDbContract.COLUMN_NAME_X_INDEX} LONG,
            ${DensityMapDbContract.COLUMN_NAME_Y_INDEX} LONG,
            ${DensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME} LONG,
            ${DensityMapDbContract.COLUMN_NAME_AMOUNT} LONG,
            UNIQUE(${DensityMapDbContract.COLUMN_NAME_X_INDEX}, ${DensityMapDbContract.COLUMN_NAME_Y_INDEX})
            )
        """.trimIndent()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(createTableSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

    }

    abstract fun subdivisions(): Int

    fun addLocation(location: Location) {
        Log.d("ogl-densitymapdbhelper", "Saving location $location")
        val (xIndex, yIndex) = toIndex(location.latitude, location.longitude)
        val locationTime = location.time

        val db = this.writableDatabase
        addOrUpdateDensityPoint(db, xIndex, yIndex, locationTime)
        Log.d("ogl-densitymapdbhelper", "Saved $location to database")
    }

    fun addPoint(lat: Double, long: Double, time: Long) {
        val (xIndex, yIndex) = toIndex(lat, long)
        val db = this.writableDatabase
        addOrUpdateDensityPoint(db, xIndex, yIndex, time)
    }


    private fun addOrUpdateDensityPoint(
        db: SQLiteDatabase,
        xIndex: Long,
        yIndex: Long,
        lastPointTime: Long
    ) {
        val updateSql = """
            UPDATE ${DensityMapDbContract.TABLE_NAME}
            SET ${DensityMapDbContract.COLUMN_NAME_AMOUNT} = ${DensityMapDbContract.COLUMN_NAME_AMOUNT} + 1,
                ${DensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME} = ?
            WHERE ${DensityMapDbContract.COLUMN_NAME_X_INDEX} = ?
              AND ${DensityMapDbContract.COLUMN_NAME_Y_INDEX} = ?
              AND (? - ${DensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME}) > 900000
        """.trimIndent()

        val insertIfNoChanges = """
            INSERT OR IGNORE INTO ${DensityMapDbContract.TABLE_NAME} (
                ${DensityMapDbContract.COLUMN_NAME_X_INDEX},
                ${DensityMapDbContract.COLUMN_NAME_Y_INDEX},
                ${DensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME},
                ${DensityMapDbContract.COLUMN_NAME_AMOUNT}
            ) 
            VALUES (?, ?, ?, 1)
        """.trimIndent()

        db.beginTransaction()
        try {
            db.execSQL(updateSql, arrayOf(lastPointTime, xIndex, yIndex, lastPointTime))
            db.execSQL(insertIfNoChanges, arrayOf(xIndex, yIndex, lastPointTime))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun toIndex(lat: Double, long: Double): Pair<Long, Long> {
        val maxLat = 85.05112878
        val clampedLat = lat.coerceIn(-maxLat, maxLat)
        val xIndex = ((long + 180.0) / 360.0 * subdivisions()).toLong()
        val latRad = Math.toRadians(clampedLat)
        val yMerc = (1.0 - ln(tan(Math.PI / 4 + latRad / 2)) / Math.PI) / 2.0
        val yIndex = (yMerc * subdivisions()).toLong()

        return Pair(xIndex, yIndex)
    }

    private fun fromIndex(xIndex: Long, yIndex: Long): Pair<Double, Double> {
        val maxLat = 85.05112878
        val n = subdivisions().toDouble()

        val long = xIndex / n * 360.0 - 180.0

        val yNorm = 1.0 - 2.0 * (yIndex.toDouble() / n)
        val latRad = atan(sinh(Math.PI * yNorm))
        val lat = Math.toDegrees(latRad).coerceIn(-maxLat, maxLat)

        return Pair(lat, long)
    }


    fun drop() {
        val db = this.writableDatabase
        db.execSQL("DELETE FROM ${DensityMapDbContract.TABLE_NAME}")
    }

    fun getPoints(bBoxDto: BBoxDto): Cursor {
        val (minX, maxY) = toIndex(bBoxDto.minLat, bBoxDto.minLon)
        val (maxX, minY) = toIndex(bBoxDto.maxLat, bBoxDto.maxLon)

        val selection = """
            ${DensityMapDbContract.COLUMN_NAME_X_INDEX} BETWEEN ? AND ? 
            AND ${DensityMapDbContract.COLUMN_NAME_Y_INDEX} BETWEEN ? AND ?
            ORDER BY ${DensityMapDbContract.COLUMN_NAME_AMOUNT} ASC
        """.trimIndent()
        val selectionArgs = arrayOf(
            minX.toString(),
            maxX.toString(),
            minY.toString(),
            maxY.toString()
        )

        return readableDatabase.query(
            DensityMapDbContract.TABLE_NAME,
            null,
            selection,
            selectionArgs,
            null,
            null,
            null
        )
    }

    fun getCoordsRange(): BBoxDto {
        val query = """
            SELECT MIN(${DensityMapDbContract.COLUMN_NAME_X_INDEX}) AS xMin, 
                MAX(${DensityMapDbContract.COLUMN_NAME_X_INDEX}) AS xMax,
                MIN(${DensityMapDbContract.COLUMN_NAME_Y_INDEX}) AS yMin, 
                MAX(${DensityMapDbContract.COLUMN_NAME_Y_INDEX}) AS yMax 
            FROM ${DensityMapDbContract.TABLE_NAME}
        """.trimIndent()

        readableDatabase.rawQuery(query, null)
            .use { cursor ->
                if (cursor.moveToFirst()) {
                    val xMin = cursor.getLong(cursor.getColumnIndexOrThrow("xMin"))
                    val xMax = cursor.getLong(cursor.getColumnIndexOrThrow("xMax"))
                    val yMin = cursor.getLong(cursor.getColumnIndexOrThrow("yMin"))
                    val yMax = cursor.getLong(cursor.getColumnIndexOrThrow("yMax"))

                    val (minLat, minLon) = fromIndex(xMin, yMin)
                    val (maxLat, maxLon) = fromIndex(xMax + 1, yMax + 1)
                    Log.d(
                        "ogl-abstractdensitymapdbhelper",
                        "Got bbox $minLon, $maxLon, $minLat, $maxLat"
                    )
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
}
