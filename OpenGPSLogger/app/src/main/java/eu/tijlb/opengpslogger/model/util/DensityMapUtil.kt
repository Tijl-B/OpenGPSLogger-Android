package eu.tijlb.opengpslogger.model.util

import android.util.Log
import eu.tijlb.opengpslogger.model.database.densitymap.continent.ContinentDensityMapDbHelper
import eu.tijlb.opengpslogger.model.database.location.LocationDbContract
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.dto.query.PointsQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

object DensityMapUtil {

    fun recreateDatabaseAsync(
        densityMapDbHelper: ContinentDensityMapDbHelper,
        locationDbHelper: LocationDbHelper
    ) {
        CoroutineScope(Dispatchers.IO)
            .launch { recreateDatabase(densityMapDbHelper, locationDbHelper) }
    }

    suspend fun recreateDatabase(
        densityMapDbHelper: ContinentDensityMapDbHelper,
        locationDbHelper: LocationDbHelper
    ) {
        densityMapDbHelper.drop()
        val pointsQuery = PointsQuery(
            dataSource = "All",
            startDateMillis = 0,
            endDateMillis = Long.MAX_VALUE,
            minAngle = 0F
        )
        locationDbHelper.getPointsCursor(pointsQuery)
            .use { cursor ->
                run {
                    if (!coroutineContext.isActive) {
                        Log.d("ogl-densitymaputil", "Stop iterating over points!")
                        return
                    }
                    Log.d("ogl-densitymaputil", "Start iterating over points cursor")
                    if (cursor.moveToFirst()) {
                        Log.d("ogl-densitymaputil", "Starting count")
                        val amountOfPointsToLoad = cursor.count
                        Log.d("ogl-densitymaputil", "Count done $amountOfPointsToLoad")

                        val latColumnIndex =
                            cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_LATITUDE)
                        val longColumnIndex =
                            cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_LONGITUDE)
                        val timeColumnIndex =
                            cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_TIMESTAMP)
                        Log.d("ogl-densitymaputil", "Got first point from cursor")
                        var i = 0
                        do {
                            if (!coroutineContext.isActive) {
                                Log.d("ogl-densitymaputil", "Stop drawing points!")
                                return
                            }

                            val longitude = cursor.getDouble(longColumnIndex)
                            val latitude = cursor.getDouble(latColumnIndex)
                            val time = cursor.getLong(timeColumnIndex)

                            densityMapDbHelper.addPoint(latitude, longitude, time)

                            if ((++i) % 10000 == 0) {
                                Log.d("ogl-densitymaputil", "Loaded $i points into the density map")
                            }
                        } while (cursor.moveToNext())
                        Log.i(
                            "ogl-densitymaputil",
                            "Finished loading $amountOfPointsToLoad points into the density map"
                        )
                    }
                }
            }
    }
}