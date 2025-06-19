package eu.tijlb.opengpslogger.model.database.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

private const val BROWSE_SETTINGS = "BROWSE_SETTINGS"

private const val CENTER_LAT = "CENTER_LAT"
private const val CENTER_LON = "CENTER_LON"
private const val ZOOM = "ZOOM"

private const val DEFAULT_CENTER_LAT = 0f
private const val DEFAULT_CENTER_LON = 0f
private const val DEFAULT_ZOOM = 4f

class BrowseSettingsHelper(val context: Context) {

    fun getCenterCoords(): Pair<Double, Double> {
        val preferences = sharedPreferences()
        val centerLat = preferences.getFloat(CENTER_LAT, DEFAULT_CENTER_LAT).toDouble()
        val centerLon = preferences.getFloat(CENTER_LON, DEFAULT_CENTER_LON).toDouble()
        Log.d("ogl-browsesettingshelper", "Got center coords $centerLat, $centerLon")
        return Pair(centerLat, centerLon)
    }

    fun getZoom(): Float {
        val zoom = sharedPreferences().getFloat(ZOOM, DEFAULT_ZOOM)
        Log.d("ogl-browsesettingshelper", "Got zoom $zoom")
        return zoom
    }

    fun setCenterCoords(lat: Double, lon: Double) {
        Log.d("ogl-browsesettingshelper", "Setting center coords to $lat $lon")
        val preferences = sharedPreferences()
        with(preferences.edit()) {
            putFloat(CENTER_LAT, lat.toFloat())
            putFloat(CENTER_LON, lon.toFloat())
            apply()
        }
    }

    fun setZoom(zoom: Float) {
        Log.d("ogl-browsesettingshelper", "Setting zoom to $zoom")
        with(sharedPreferences().edit()) {
            putFloat(ZOOM, zoom)
            apply()
        }
    }

    private fun sharedPreferences(): SharedPreferences =
        context.getSharedPreferences(BROWSE_SETTINGS, Context.MODE_PRIVATE)

}