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
        private val neighborsQuery = """
                    SELECT ${BaseColumns._ID}, 
                           ${LocationDbContract.COLUMN_NAME_LATITUDE},
                           ${LocationDbContract.COLUMN_NAME_LONGITUDE},
                           ${LocationDbContract.COLUMN_NAME_TIMESTAMP}
                    FROM ${LocationDbContract.TABLE_NAME}
                    WHERE ${LocationDbContract.COLUMN_NAME_SOURCE} = ? 
                      AND ${BaseColumns._ID} >= ?
                      AND ${BaseColumns._ID} <= ?
                    ORDER BY ${BaseColumns._ID} ASC
                """.trimIndent()

        private val updateDistAngleQuery = """
                UPDATE ${LocationDbContract.TABLE_NAME}
                SET ${LocationDbContract.COLUMN_NAME_NEIGHBOR_DISTANCE} = ?,
                    ${LocationDbContract.COLUMN_NAME_NEIGHBOR_ANGLE} = ?
                WHERE ${BaseColumns._ID} = ?
                """.trimIndent()

        private val batchQuery = """
                SELECT ${BaseColumns._ID},
                        ${LocationDbContract.COLUMN_NAME_SOURCE}, 
                        ${LocationDbContract.COLUMN_NAME_TIMESTAMP}
                FROM ${LocationDbContract.TABLE_NAME}
                WHERE ${LocationDbContract.COLUMN_NAME_NEIGHBOR_DISTANCE} IS NULL
                    AND ${LocationDbContract.COLUMN_NAME_SOURCE} IS NOT NULL
                ORDER BY ${BaseColumns._ID} ASC
                LIMIT 5000
                """.trimIndent()

        private val maxIdQuery = """
            SELECT MAX(${BaseColumns._ID}) AS highest_id FROM ${LocationDbContract.TABLE_NAME}
        """.trimIndent()

        @SuppressLint("Range")
        fun updateBatch(readDb: SQLiteDatabase, writeDb: SQLiteDatabase): Int {
            val rowsToUpdate = mutableListOf<Triple<Long, String, Long>>()

            readDb.rawQuery(batchQuery, null).use {
                if (it.moveToFirst()) {
                    val columnIndexId = it.getColumnIndex(BaseColumns._ID)
                    val columnIndexSource = it.getColumnIndex(LocationDbContract.COLUMN_NAME_SOURCE)
                    val columnIndexTimestamp =
                        it.getColumnIndex(LocationDbContract.COLUMN_NAME_TIMESTAMP)
                    do {
                        val id = it.getLong(columnIndexId)
                        val source = it.getString(columnIndexSource)
                        val timestamp = it.getLong(columnIndexTimestamp)
                        rowsToUpdate.add(Triple(id, source, timestamp))
                    } while (it.moveToNext())
                }
            }

            Log.d(
                "ogl-locationdbneighborsutil",
                "Calculating distance and angle for ${rowsToUpdate.size} points"
            )
            val idDistAngle = rowsToUpdate.mapNotNull {
                calculateDistAngle(readDb, it.second, it.third, it.first)
            }

            Log.d(
                "ogl-locationdbneighborsutil",
                "Done calculating distance and angle for ${rowsToUpdate.size} points."
            )

            writeDb.beginTransaction()
            try {
                val statement = writeDb.compileStatement(updateDistAngleQuery)

                idDistAngle.forEach { (id, dist, angle) ->
                    statement.clearBindings()
                    statement.bindDouble(1, dist)
                    angle?.let { statement.bindDouble(2, it) }
                        ?: run { statement.bindNull(2) }
                    statement.bindLong(3, id)
                    statement.executeUpdateDelete()
                }

                writeDb.setTransactionSuccessful()
            } finally {
                writeDb.endTransaction()
            }
            Log.d(
                "ogl-locationdbneighborsutil",
                "Done updating distance and angle for ${rowsToUpdate.size} points (wrote ${idDistAngle.size}, checked id ${rowsToUpdate.first()} to ${rowsToUpdate.last()})"
            )
            return idDistAngle.size
        }

        private fun calculateDistAngle(
            db: SQLiteDatabase,
            source: String,
            timestamp: Long,
            id: Long
        ): Triple<Long, Double, Double?>? {
            val neighbors = findNeighbors(db, source, timestamp, id)
            when (neighbors.size) {
                3 -> {
                    val prevPoint = neighbors[0]
                    val middlePoint = neighbors[1]
                    val nextPoint = neighbors[2]
                    val distance = calculateDistance(prevPoint, middlePoint, nextPoint)
                    val angle = calculateAngle(prevPoint, middlePoint, nextPoint)

                    return Triple(middlePoint.id, distance, angle)
                }

                2 -> {
                    getMaxId(db)
                        ?.let {
                            if (neighbors[1].id < it) {
                                val distance = calculateDistance(neighbors[0], neighbors[1])
                                return Triple(id, distance, null)
                            }
                        }
                }

                1 -> {
                    getMaxId(db)
                        ?.let {
                            if (neighbors[0].id < it) {
                                return Triple(id, 0.0, null)
                            }
                        }
                }
            }
            return null
        }

        private fun getMaxId(db: SQLiteDatabase): Long? {
            var maxId: Long? = null
            db.rawQuery(maxIdQuery, null)
                .use {
                    if (it.moveToFirst()) {
                        val highestId = it.getLong(it.getColumnIndexOrThrow("highest_id"))
                        Log.d("ogl-locationdbneighborsutil", "Highest AUTOINCREMENT ID: $highestId")
                        maxId = highestId
                    }
                }
            return maxId
        }

        @SuppressLint("Range")
        private fun findNeighbors(
            db: SQLiteDatabase,
            source: String,
            timestamp: Long,
            id: Long
        ): MutableList<OutlierUtil.Point> {
            val last3Points = mutableListOf<OutlierUtil.Point>()

            db.rawQuery(
                neighborsQuery,
                arrayOf(
                    source,
                    (id - 1).toString(),
                    (id + 1).toString()
                )
            ).use {
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