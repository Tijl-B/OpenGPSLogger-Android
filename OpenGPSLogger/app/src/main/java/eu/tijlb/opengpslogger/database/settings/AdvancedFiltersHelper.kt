package eu.tijlb.opengpslogger.database.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

private const val ADVANCED_FILTERS = "ADVANCED_FILTERS"

private const val MIN_ACCURACY = "MIN_ACCURACY"
private const val MIN_SPEED = "MIN_SPEED"
private const val MAX_SPEED = "MAX_SPEED"

private const val NO_MIN_ACCURACY = -1F
private const val DEFAULT_MIN_ACCURACY = NO_MIN_ACCURACY

class AdvancedFiltersHelper(val context: Context) {

    fun getMinAccuracy(): Float? {
        val locationRequestPreferences =
            context.getSharedPreferences(ADVANCED_FILTERS, Context.MODE_PRIVATE)
        val accuracy = locationRequestPreferences.getFloat(MIN_ACCURACY, DEFAULT_MIN_ACCURACY)
        return accuracy.takeUnless { it == NO_MIN_ACCURACY }
    }

    fun setMinAccuracy(accuracy: Float?) {
        val locationRequestPreferences =
            context.getSharedPreferences(ADVANCED_FILTERS, Context.MODE_PRIVATE)
        with(locationRequestPreferences.edit()) {
            putFloat(MIN_ACCURACY, accuracy?:NO_MIN_ACCURACY)
            apply()
        }
    }

    fun registerMinAccuracyChangedListener(function: (Float?) -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        Log.d("ogl-advancedfiltershelper", "Registering active changed listener")
        val preferences = context.getSharedPreferences(ADVANCED_FILTERS, Context.MODE_PRIVATE)

        val preferencesListener =
            SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                Log.d(
                    "ogl-advancedfiltershelper",
                    "SharedPreferences.OnSharedPreferenceChangeListener called for key $key"
                )
                if (key == MIN_ACCURACY) {
                    sharedPreferences.getFloat(MIN_ACCURACY, DEFAULT_MIN_ACCURACY)
                        .takeUnless { it == NO_MIN_ACCURACY }
                        .let { function(it) }
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(preferencesListener)
        return preferencesListener
    }

    fun deregisterAdvancedFiltersChangedListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val preferences = context.getSharedPreferences(ADVANCED_FILTERS, Context.MODE_PRIVATE)
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
