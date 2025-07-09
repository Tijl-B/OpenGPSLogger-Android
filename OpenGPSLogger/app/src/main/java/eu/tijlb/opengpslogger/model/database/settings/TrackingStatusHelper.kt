package eu.tijlb.opengpslogger.model.database.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

private const val TRACKING_STATUS = "TRACKING_STATUS"

private const val ACTIVE = "ACTIVE"

private const val TAG = "ogl-trackingstatushelper"

class TrackingStatusHelper(val context: Context) {

    fun isActive(): Boolean {
        val preferences =
            getPreferences()
        return preferences.getBoolean(ACTIVE, false)
    }

    fun setActive(active: Boolean) {
        val locationRequestPreferences =
            getPreferences()
        with(locationRequestPreferences.edit()) {
            putBoolean(ACTIVE, active)
            apply()
        }
    }

    fun registerActiveChangedListener(function: (Boolean) -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        Log.d(TAG, "Registering active changed listener")
        val preferences = getPreferences()

        val preferencesListener =
            SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                Log.d(TAG, "SharedPreferences.OnSharedPreferenceChangeListener called for key $key")
                if (key == ACTIVE) {
                    sharedPreferences.getBoolean(ACTIVE, false)
                        .let { function(it) }
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(preferencesListener)
        return preferencesListener
    }

    fun deregisterActiveChangedListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val preferences = getPreferences()
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun getPreferences(): SharedPreferences {
        Log.d(TAG, "Getting shared preferences of $TRACKING_STATUS")
        return context.getSharedPreferences(TRACKING_STATUS, Context.MODE_PRIVATE)
    }
}
