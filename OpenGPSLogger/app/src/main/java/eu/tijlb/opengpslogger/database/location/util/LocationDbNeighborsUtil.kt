package eu.tijlb.opengpslogger.database.location.util

import android.annotation.SuppressLint
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import android.util.Log
import eu.tijlb.opengpslogger.database.location.LocationDbContract
import eu.tijlb.opengpslogger.util.OutlierUtil
import eu.tijlb.opengpslogger.util.OutlierUtil.Companion.calculateAngle
import eu.tijlb.opengpslogger.util.OutlierUtil.Companion.calculateDistance

class LocationDbNeighborsUtil {
    companion object {
        @SuppressLint("Range")
        fun updateBatch(db: SQLiteDatabase): Int {
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

            Log.d(
                "ogl-locationdbhelper",
                "Updating distance and angle for ${rowsToUpdate.size} points"
            )
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
            val last3 = findNeighbors(db, source, timestamp)
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
        private fun findNeighbors(
            db: SQLiteDatabase,
            source: String,
            timestamp: Long
        ): MutableList<OutlierUtil.Point> {
            val cursor = db.rawQuery(
                """
        SELECT * FROM (
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
        UNION SELECT * FROM
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
        UNION SELECT * FROM
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
                    val columnIndexLatitude =
                        it.getColumnIndex(LocationDbContract.COLUMN_NAME_LATITUDE)
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
    }
}