package eu.tijlb.opengpslogger.database.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import eu.tijlb.opengpslogger.dto.VisualisationSettingsDto

private const val VISUALISATION_SETTINGS = "VISUALISATION_SETTINGS"

private const val DRAW_LINES = "DRAW_LINES"
private const val DOT_SIZE = "DOT_SIZE"
private const val LINE_SIZE = "LINE_SIZE"
private const val CONNECT_LINE_MAX_MINS_DELTA = "CONNECT_LINE_MAX_MINS_DELTA"

private const val DEFAULT_DRAW_LINES = false
private const val DEFAULT_CONNECT_LINE_MAX_MINS_DELTA = 15L

private const val AUTO_DOT_SIZE = -1F
private const val AUTO_LINE_SIZE = -1F
private const val DEFAULT_DOT_SIZE = AUTO_DOT_SIZE
private const val DEFAULT_LINE_SIZE = AUTO_LINE_SIZE

class VisualisationSettingsHelper(val context: Context) {

    fun getVisualisationSettings(): VisualisationSettingsDto {
        val locationRequestPreferences =
            context.getSharedPreferences(VISUALISATION_SETTINGS, Context.MODE_PRIVATE)
        val drawLines = locationRequestPreferences.getBoolean(DRAW_LINES, DEFAULT_DRAW_LINES)
        val dotSize = locationRequestPreferences.getFloat(DOT_SIZE, DEFAULT_DOT_SIZE)
        val lineSize = locationRequestPreferences.getFloat(LINE_SIZE, DEFAULT_LINE_SIZE)
        val connectLineMaxMinsDelta = locationRequestPreferences.getLong(
            CONNECT_LINE_MAX_MINS_DELTA, DEFAULT_CONNECT_LINE_MAX_MINS_DELTA
        )
        return VisualisationSettingsDto(
            drawLines,
            lineSize.takeUnless { it == AUTO_LINE_SIZE },
            dotSize.takeUnless { it == AUTO_DOT_SIZE },
            connectLineMaxMinsDelta
        )
    }

    fun setVisualisationSettings(settingsDto: VisualisationSettingsDto) {
        Log.d("ogl-visualisationsettingshelper", "Updating visualisation settings to $settingsDto")
        val locationRequestPreferences =
            context.getSharedPreferences(VISUALISATION_SETTINGS, Context.MODE_PRIVATE)
        with(locationRequestPreferences.edit()) {
            putBoolean(DRAW_LINES, settingsDto.drawLines)
            putFloat(DOT_SIZE, settingsDto.dotSize ?: AUTO_DOT_SIZE)
            putFloat(LINE_SIZE, settingsDto.lineSize ?: AUTO_LINE_SIZE)
            putLong(CONNECT_LINE_MAX_MINS_DELTA, settingsDto.connectLinesMaxMinutesDelta)
            apply()
        }
    }

    fun registerVisualisationSettingsChangedListener(function: (VisualisationSettingsDto) -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        Log.d("ogl-advancedfiltershelper", "Registering active changed listener")
        val preferences = context.getSharedPreferences(VISUALISATION_SETTINGS, Context.MODE_PRIVATE)

        val preferencesListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                Log.d(
                    "ogl-advancedfiltershelper",
                    "SharedPreferences.OnSharedPreferenceChangeListener called for key $key"
                )
                function(getVisualisationSettings())
            }
        preferences.registerOnSharedPreferenceChangeListener(preferencesListener)
        return preferencesListener
    }

    fun deregisterAdvancedFiltersChangedListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val preferences = context.getSharedPreferences(VISUALISATION_SETTINGS, Context.MODE_PRIVATE)
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
}