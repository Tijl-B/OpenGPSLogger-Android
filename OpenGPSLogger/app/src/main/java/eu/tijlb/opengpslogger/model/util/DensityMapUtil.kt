package eu.tijlb.opengpslogger.model.util

import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import eu.tijlb.opengpslogger.model.database.densitymap.DensityMapAdapter
import eu.tijlb.opengpslogger.model.database.location.LocationDbContract
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.dto.query.PointsQuery
import eu.tijlb.opengpslogger.ui.util.LockUtil.runIfLast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

private const val TAG = "ogl-densitymaputil"

class DensityMapUtil(val context: Context) {

    val mutex = Mutex()
    val jobReference: AtomicReference<Job?> = AtomicReference(null)

    fun recreateDatabaseAsync(
        densityMapAdapter: DensityMapAdapter,
        locationDbHelper: LocationDbHelper
    ) {
        CoroutineScope(Dispatchers.IO)
            .launch {
                mutex.runIfLast(jobReference) {
                    recreateDatabase(densityMapAdapter, locationDbHelper)
                }
            }
    }

    private suspend fun recreateDatabase(
        densityMapAdapter: DensityMapAdapter,
        locationDbHelper: LocationDbHelper
    ) {
        densityMapAdapter.drop()
        val pointsQuery = PointsQuery()
        locationDbHelper.getPointsCursor(pointsQuery)
            .use { cursor ->
                run {
                    if (!coroutineContext.isActive) {
                        Log.d(TAG, "Stop iterating over points!")
                        return
                    }
                    Log.d(TAG, "Start iterating over points cursor")
                    if (cursor.moveToFirst()) {
                        Log.d(TAG, "Starting count")
                        val amountOfPointsToLoad = cursor.count
                        Log.d(TAG, "Count done $amountOfPointsToLoad")

                        val latColumnIndex =
                            cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_LATITUDE)
                        val longColumnIndex =
                            cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_LONGITUDE)
                        val timeColumnIndex =
                            cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_TIMESTAMP)
                        Log.d(TAG, "Got first point from cursor")
                        var i = 0
                        do {
                            if (!coroutineContext.isActive) {
                                Log.d(TAG, "Stop drawing points!")
                                return
                            }

                            val longitude = cursor.getDouble(longColumnIndex)
                            val latitude = cursor.getDouble(latColumnIndex)
                            val time = cursor.getLong(timeColumnIndex)

                            densityMapAdapter.addPoint(latitude, longitude, time)

                            if ((++i) % 2000 == 0) {
                                Log.d(TAG, "Loaded $i points into the density map")
                                broadcastDensityMapUpdated()
                            }
                        } while (cursor.moveToNext())
                        Log.i(
                            TAG,
                            "Finished loading $amountOfPointsToLoad points into the density map"
                        )
                    }
                }
            }
    }

    fun broadcastDensityMapUpdated(location: Location? = null) {
        val intent = Intent("eu.tijlb.LOCATION_DM_UPDATE")
        intent.putExtra("location", location)
        intent.setPackage(context.packageName)
        Log.d(TAG, "Broadcast location $location")
        context.sendBroadcast(intent)
    }
}