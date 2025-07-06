package eu.tijlb.opengpslogger.model.database.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

private const val ADVANCED_FILTERS = "ADVANCED_FILTERS"

private const val MIN_ACCURACY = "MIN_ACCURACY"
private const val MIN_ANGLE = "MIN_ANGLE"

private const val NO_MIN_ACCURACY = -1F
private const val DEFAULT_MIN_ACCURACY = NO_MIN_ACCURACY
private const val DEFAULT_MIN_ANGLE = 0F

private const val TAG = "ogl-advancedfiltershelper"

class AdvancedFiltersHelper(val context: Context) {

    fun getMinAccuracy(): Float? {
        val locationRequestPreferences = getLocationRequestPreferences()
        val accuracy = locationRequestPreferences.getFloat(MIN_ACCURACY, DEFAULT_MIN_ACCURACY)
        return accuracy.takeUnless { it == NO_MIN_ACCURACY }
    }

    fun setMinAccuracy(accuracy: Float?) {
        val locationRequestPreferences = getLocationRequestPreferences()
        with(locationRequestPreferences.edit()) {
            putFloat(MIN_ACCURACY, accuracy ?: NO_MIN_ACCURACY)
            apply()
        }
    }

    fun getMinAngle(): Float {
        val locationRequestPreferences = getLocationRequestPreferences()
        val accuracy = locationRequestPreferences.getFloat(MIN_ANGLE, DEFAULT_MIN_ANGLE)
        return accuracy
    }

    fun setMinAngle(angle: Float?) {
        val boundedAngle = angle?.coerceIn(0F, 180F)
        val locationRequestPreferences = getLocationRequestPreferences()
        with(locationRequestPreferences.edit()) {
            putFloat(MIN_ANGLE, boundedAngle ?: DEFAULT_MIN_ANGLE)
            apply()
        }
    }

    fun registerMinAccuracyChangedListener(function: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        Log.d(TAG, "Registering active changed listener")
        val preferences = getLocationRequestPreferences()

        val preferencesListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                Log.d(
                    TAG,
                    "SharedPreferences.OnSharedPreferenceChangeListener called for key $key"
                )
                function()
            }
        preferences.registerOnSharedPreferenceChangeListener(preferencesListener)
        return preferencesListener
    }

    fun deregisterAdvancedFiltersChangedListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val preferences = getLocationRequestPreferences()
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun getLocationRequestPreferences(): SharedPreferences {
        Log.d(TAG, "Getting shared preferences of $ADVANCED_FILTERS")
        return context.getSharedPreferences(ADVANCED_FILTERS, Context.MODE_PRIVATE)
    }
}
