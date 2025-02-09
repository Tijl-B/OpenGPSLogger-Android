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
            putFloat(MIN_ACCURACY, accuracy ?: NO_MIN_ACCURACY)
            apply()
        }
    }

    fun getMinAngle(): Float {
        val locationRequestPreferences =
            context.getSharedPreferences(ADVANCED_FILTERS, Context.MODE_PRIVATE)
        val accuracy = locationRequestPreferences.getFloat(MIN_ANGLE, DEFAULT_MIN_ANGLE)
        return accuracy
    }

    fun setMinAngle(angle: Float?) {
        val boundedAngle = angle?.coerceIn(0F, 180F)
        val locationRequestPreferences =
            context.getSharedPreferences(ADVANCED_FILTERS, Context.MODE_PRIVATE)
        with(locationRequestPreferences.edit()) {
            putFloat(MIN_ANGLE, boundedAngle ?: DEFAULT_MIN_ANGLE)
            apply()
        }
    }

    fun registerMinAccuracyChangedListener(function: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        Log.d("ogl-advancedfiltershelper", "Registering active changed listener")
        val preferences = context.getSharedPreferences(ADVANCED_FILTERS, Context.MODE_PRIVATE)

        val preferencesListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                Log.d(
                    "ogl-advancedfiltershelper",
                    "SharedPreferences.OnSharedPreferenceChangeListener called for key $key"
                )
                function()
            }
        preferences.registerOnSharedPreferenceChangeListener(preferencesListener)
        return preferencesListener
    }

    fun deregisterAdvancedFiltersChangedListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val preferences = context.getSharedPreferences(ADVANCED_FILTERS, Context.MODE_PRIVATE)
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
