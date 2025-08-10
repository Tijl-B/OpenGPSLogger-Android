package eu.tijlb.opengpslogger.model.database.lastlocation

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.util.Log

private const val LAST_LOCATION = "LAST_LOCATION"

private const val LAST_LAT = "LAST_LAT"
private const val LAST_LON = "LAST_LON"
private const val TIMESTAMP = "TIMESTAMP"

private const val DEFAULT_CENTER_LAT = 0F
private const val DEFAULT_CENTER_LON = 0F
private const val DEFAULT_TIMESTAMP = -1L

private const val TAG = "ogl-browsesettingshelper"

class LastLocationHelper(val context: Context) {

    fun getLastLocation(): Location? {
        val preferences = sharedPreferences()
        val timestamp = preferences.getLong(TIMESTAMP, DEFAULT_TIMESTAMP)
        if (timestamp == DEFAULT_TIMESTAMP) {
            return null;
        }

        val lastLat = preferences.getFloat(LAST_LAT, DEFAULT_CENTER_LAT).toDouble()
        val lastLon = preferences.getFloat(LAST_LON, DEFAULT_CENTER_LON).toDouble()
        Log.d(TAG, "Got last coords $lastLat, $lastLon")
        val location = Location("ogl")
        location.latitude = lastLat
        location.longitude = lastLon
        location.time = timestamp
        return location
    }

    fun setLastLocation(location: Location) {
        Log.d(TAG, "Setting last location to $location")
        val preferences = sharedPreferences()
        with(preferences.edit()) {
            putFloat(LAST_LAT, location.latitude.toFloat())
            putFloat(LAST_LON, location.longitude.toFloat())
            putLong(TIMESTAMP, location.time)
            apply()
        }
    }

    private fun sharedPreferences(): SharedPreferences {
        Log.d(TAG, "Getting shared preferences of $LAST_LOCATION")
        return context.getSharedPreferences(LAST_LOCATION, Context.MODE_PRIVATE)
    }
}