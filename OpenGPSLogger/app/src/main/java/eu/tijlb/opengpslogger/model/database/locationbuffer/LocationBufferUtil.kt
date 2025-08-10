package eu.tijlb.opengpslogger.model.database.locationbuffer

import android.content.Context
import android.content.Intent
import android.location.Location
import android.provider.BaseColumns
import android.util.Log
import eu.tijlb.opengpslogger.model.database.densitymap.DensityMapAdapter
import eu.tijlb.opengpslogger.model.database.lastlocation.LastLocationHelper
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

private const val TAG = "ogl-locationbufferutil"

class LocationBufferUtil(val context: Context) {
    val densityMapAdapter = DensityMapAdapter.getInstance(context)
    val locationDbHelper = LocationDbHelper.getInstance(context)
    val locationBufferDbHelper = LocationBufferDbHelper.getInstance(context)
    val lastLocationHelper = LastLocationHelper(context)

    suspend fun flushBuffer() {
        Log.d(TAG, "Writing location buffer to db")
        var pointsWritten = 0
        var lastLocation: Location? = null
        locationBufferDbHelper.getPointsCursor()
            .use { cursor ->
                if (cursor.moveToFirst()) {
                    val indexIdx = cursor.getColumnIndex(BaseColumns._ID)
                    val timestampIdx =
                        cursor.getColumnIndex(LocationBufferDbContract.COLUMN_NAME_TIMESTAMP)
                    val latitudeIdx =
                        cursor.getColumnIndex(LocationBufferDbContract.COLUMN_NAME_LATITUDE)
                    val longitudeIdx =
                        cursor.getColumnIndex(LocationBufferDbContract.COLUMN_NAME_LONGITUDE)
                    val speedIdx =
                        cursor.getColumnIndex(LocationBufferDbContract.COLUMN_NAME_SPEED)
                    val speedAccuracyIdx =
                        cursor.getColumnIndex(LocationBufferDbContract.COLUMN_NAME_SPEED_ACCURACY)
                    val accuracyIdx =
                        cursor.getColumnIndex(LocationBufferDbContract.COLUMN_NAME_ACCURACY)
                    val sourceIdx =
                        cursor.getColumnIndex(LocationBufferDbContract.COLUMN_NAME_SOURCE)
                    do {
                        if (!coroutineContext.isActive) {
                            Log.d(TAG, "Interrupting...")
                            return
                        }

                        val index = cursor.getInt(indexIdx)
                        val timeStamp = cursor.getLong(timestampIdx)
                        val latitude = cursor.getDouble(latitudeIdx)
                        val longitude = cursor.getDouble(longitudeIdx)
                        val speed = cursor.getFloat(speedIdx)
                        val speedAccuracy = cursor.getFloat(speedAccuracyIdx)
                        val accuracy = cursor.getFloat(accuracyIdx)
                        val source = cursor.getString(sourceIdx)

                        val location = Location(source)
                        location.time = timeStamp
                        location.latitude = latitude
                        location.longitude = longitude
                        location.speed = speed
                        location.speedAccuracyMetersPerSecond = speedAccuracy
                        location.accuracy = accuracy

                        locationDbHelper.save(location, source)
                        densityMapAdapter.addLocation(location)

                        locationBufferDbHelper.remove(index)
                        broadCastLocationUpdated(location)
                        lastLocation = location
                        pointsWritten++
                    } while (cursor.moveToNext())
                }
            }
        lastLocation?.let {
            lastLocationHelper.setLastLocation(it)
            broadCastLocationUpdated(it)
        }
        Log.d(TAG, "Wrote $pointsWritten locations from buffer to db")
    }

    private fun broadCastLocationUpdated(location: Location) {
        val intent = Intent("eu.tijlb.LOCATION_DB_UPDATE")
        intent.putExtra("location", location)
        intent.setPackage(context.packageName)
        Log.d(TAG, "Broadcast location $location")
        context.sendBroadcast(intent)
    }
}