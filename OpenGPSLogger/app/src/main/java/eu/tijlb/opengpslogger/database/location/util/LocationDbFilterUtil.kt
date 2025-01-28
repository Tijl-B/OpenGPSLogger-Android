package eu.tijlb.opengpslogger.database.location.util

import android.util.Log
import eu.tijlb.opengpslogger.database.location.LocationDbContract
import eu.tijlb.opengpslogger.query.PointsQuery

class LocationDbFilterUtil {
    companion object {
        fun getFilter(query: PointsQuery): String {
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
            val angleFilter =
                query.minAngle
                    .takeUnless { it == 0F }
                    ?.let {
                        """
                       (
                            ${LocationDbContract.COLUMN_NAME_NEIGHBOR_ANGLE} >= $it
                            OR ${LocationDbContract.COLUMN_NAME_NEIGHBOR_ANGLE} IS NULL
                        )
                        AND
                        """.trimIndent()
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
             $angleFilter
             $timestampFilter
              ${if (query.dataSource != "All") "AND ${LocationDbContract.COLUMN_NAME_SOURCE} = '${query.dataSource}'" else ""}
            """.trimIndent()
            Log.d("ogl-locationdbhelper-filter", "Using filter $filter")
            return filter
        }
    }
}