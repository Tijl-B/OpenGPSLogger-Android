package eu.tijlb.opengpslogger.database.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

private const val TRACKING_STATUS = "TRACKING_STATUS"

private const val ACTIVE = "ACTIVE"

class TrackingStatusHelper(val context: Context) {

    fun isActive(): Boolean {
        val preferences =
            context.getSharedPreferences(TRACKING_STATUS, Context.MODE_PRIVATE)
        return preferences.getBoolean(ACTIVE, false)
    }

    fun setActive(active: Boolean) {
        val locationRequestPreferences =
            context.getSharedPreferences(TRACKING_STATUS, Context.MODE_PRIVATE)
        with(locationRequestPreferences.edit()) {
            putBoolean(ACTIVE, active)
            apply()
        }
    }

    fun registerActiveChangedListener(function: (Boolean) -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        Log.d("ogl-trackingstatushelper", "Registering active changed listener")
        val preferences = context.getSharedPreferences(TRACKING_STATUS, Context.MODE_PRIVATE)

        val preferencesListener =
            SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                Log.d(
                    "ogl-trackingstatushelper",
                    "SharedPreferences.OnSharedPreferenceChangeListener called for key $key"
                )
                if (key == ACTIVE) {
                    sharedPreferences.getBoolean(ACTIVE, false)
                        .let { function(it) }
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(preferencesListener)
        return preferencesListener
    }

    fun deregisterActiveChangedListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val preferences = context.getSharedPreferences(TRACKING_STATUS, Context.MODE_PRIVATE)
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
