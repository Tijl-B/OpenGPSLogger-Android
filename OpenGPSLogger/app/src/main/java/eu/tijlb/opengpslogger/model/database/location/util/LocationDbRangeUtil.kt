package eu.tijlb.opengpslogger.model.database.location.util

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import eu.tijlb.opengpslogger.model.database.location.LocationDbContract
import eu.tijlb.opengpslogger.model.database.location.util.LocationDbFilterUtil.getFilter
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.model.dto.query.PointsQuery

object LocationDbRangeUtil {
    fun getTimeRange(db: SQLiteDatabase, query: PointsQuery): Pair<Long, Long> {
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

    fun getCoordsRange(db: SQLiteDatabase, query: PointsQuery): BBoxDto {
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
                    Log.d("ogl-locationdbrangeutil", "Got min lon $minLon, max lon $maxLon")
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
}
