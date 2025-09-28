package eu.tijlb.opengpslogger.model.util

import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import eu.tijlb.opengpslogger.model.database.densitymap.DensityMapAdapter
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.dto.query.PointsQuery
import eu.tijlb.opengpslogger.ui.util.LockUtil.runIfLast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicReference

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
        locationDbHelper.getPointsFlow(pointsQuery)
            .collectIndexed { i, point ->
                densityMapAdapter.addPoint(point)

                if ((i) % 2000 == 0) {
                    Log.d(TAG, "Loaded $i points into the density map")
                    broadcastDensityMapUpdated()
                }
            }

        broadcastDensityMapUpdated()
        Log.i(
            TAG,
            "Finished loading all points into the density map"
        )
    }

    fun broadcastDensityMapUpdated(location: Location? = null) {
        val intent = Intent("eu.tijlb.LOCATION_DM_UPDATE")
        intent.putExtra("location", location)
        intent.setPackage(context.packageName)
        Log.d(TAG, "Broadcast location $location")
        context.sendBroadcast(intent)
    }
}