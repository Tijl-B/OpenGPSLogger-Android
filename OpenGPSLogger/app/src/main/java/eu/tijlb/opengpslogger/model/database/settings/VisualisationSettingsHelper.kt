package eu.tijlb.opengpslogger.model.database.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import eu.tijlb.opengpslogger.model.dto.VisualisationSettingsDto
import androidx.core.content.edit

private const val VISUALISATION_SETTINGS = "VISUALISATION_SETTINGS"

private const val DRAW_LINES = "DRAW_LINES"
private const val DRAW_DENSITY_MAP = "DRAW_DENSITY_MAP"
private const val DOT_SIZE = "DOT_SIZE"
private const val LINE_SIZE = "LINE_SIZE"
private const val CONNECT_LINE_MAX_MINS_DELTA = "CONNECT_LINE_MAX_MINS_DELTA"
private const val COLOR_MODE = "COLOR_MODE"
private const val COLOR_SEED = "COLOR_SEED"
private const val COLOR_OPACITY_PERCENTAGE = "COLOR_OPACITY_PERCENTAGE"
private const val SHOW_LAST_LOCATION = "SHOW_LAST_LOCATION"

private const val DEFAULT_DRAW_LINES = false
private const val DEFAULT_DRAW_DENSITY_MAP = false
private const val DEFAULT_CONNECT_LINE_MAX_MINS_DELTA = 15L
private val DEFAULT_COLOR_MODE = ColorMode.MULTI_COLOR_MONTH.name
private const val DEFAULT_COLOR_SEED = 0
private const val DEFAULT_COLOR_OPACITY_PERCENTAGE = 100
private const val DEFAULT_SHOW_LAST_LOCATION = false

private const val AUTO_DOT_SIZE = -1F
private const val AUTO_LINE_SIZE = -1F
private const val DEFAULT_DOT_SIZE = AUTO_DOT_SIZE
private const val DEFAULT_LINE_SIZE = AUTO_LINE_SIZE

private const val TAG = "ogl-advancedfiltershelper"

class VisualisationSettingsHelper(val context: Context) {

    fun getVisualisationSettings(): VisualisationSettingsDto {
        val locationRequestPreferences = getLocationRequestPreferences()
        val drawLines = locationRequestPreferences.getBoolean(DRAW_LINES, DEFAULT_DRAW_LINES)
        val drawDensityMap =
            locationRequestPreferences.getBoolean(DRAW_DENSITY_MAP, DEFAULT_DRAW_DENSITY_MAP)
        val dotSize = locationRequestPreferences.getFloat(DOT_SIZE, DEFAULT_DOT_SIZE)
        val lineSize = locationRequestPreferences.getFloat(LINE_SIZE, DEFAULT_LINE_SIZE)
        val connectLineMaxMinsDelta = locationRequestPreferences.getLong(
            CONNECT_LINE_MAX_MINS_DELTA, DEFAULT_CONNECT_LINE_MAX_MINS_DELTA
        )
        val colorSeed = locationRequestPreferences.getInt(COLOR_SEED, DEFAULT_COLOR_SEED)
        val colorOpacity = locationRequestPreferences.getInt(
            COLOR_OPACITY_PERCENTAGE,
            DEFAULT_COLOR_OPACITY_PERCENTAGE
        )
        val colorModeStr = locationRequestPreferences.getString(COLOR_MODE, DEFAULT_COLOR_MODE)
            ?.takeIf { colorModePref ->
                ColorMode.entries.map { it.name }
                    .contains(colorModePref)
            }
            ?: DEFAULT_COLOR_MODE
        val colorMode = colorModeStr.let { ColorMode.valueOf(it) }
        return VisualisationSettingsDto(
            drawLines,
            drawDensityMap,
            lineSize.takeUnless { it == AUTO_LINE_SIZE },
            dotSize.takeUnless { it == AUTO_DOT_SIZE },
            connectLineMaxMinsDelta,
            colorMode,
            colorSeed,
            colorOpacity
        )
    }

    fun setVisualisationSettings(settingsDto: VisualisationSettingsDto) {
        Log.d("ogl-visualisationsettingshelper", "Updating visualisation settings to $settingsDto")
        val locationRequestPreferences = getLocationRequestPreferences()
        locationRequestPreferences.edit {
            putBoolean(DRAW_LINES, settingsDto.drawLines)
            putBoolean(DRAW_DENSITY_MAP, settingsDto.drawDensityMap)
            putFloat(DOT_SIZE, settingsDto.dotSize ?: AUTO_DOT_SIZE)
            putFloat(LINE_SIZE, settingsDto.lineSize ?: AUTO_LINE_SIZE)
            putLong(CONNECT_LINE_MAX_MINS_DELTA, settingsDto.connectLinesMaxMinutesDelta)
            putInt(COLOR_SEED, settingsDto.colorSeed)
            putInt(COLOR_OPACITY_PERCENTAGE, settingsDto.opacityPercentage)
            putString(COLOR_MODE, settingsDto.colorMode.toString())
        }
    }

    fun setShowLastLocation(boolean: Boolean) {
        val locationRequestPreferences = getLocationRequestPreferences()
        locationRequestPreferences.edit() {
            putBoolean(SHOW_LAST_LOCATION, boolean)
        }
    }

    fun getShowLastLocation(): Boolean {
        val locationRequestPreferences = getLocationRequestPreferences()
        val showLastLocation =
            locationRequestPreferences.getBoolean(SHOW_LAST_LOCATION, DEFAULT_SHOW_LAST_LOCATION)
        return showLastLocation
    }

    fun registerVisualisationSettingsChangedListener(function: (VisualisationSettingsDto) -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        Log.d(TAG, "Registering active changed listener")
        val preferences = getLocationRequestPreferences()

        val preferencesListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                Log.d(
                    TAG,
                    "SharedPreferences.OnSharedPreferenceChangeListener called for key $key"
                )
                function(getVisualisationSettings())
            }
        preferences.registerOnSharedPreferenceChangeListener(preferencesListener)
        return preferencesListener
    }

    fun deregisterAdvancedFiltersChangedListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val preferences = getLocationRequestPreferences()
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun getLocationRequestPreferences(): SharedPreferences {
        Log.d(TAG, "Getting shared preferences of $VISUALISATION_SETTINGS")
        return context.getSharedPreferences(VISUALISATION_SETTINGS, Context.MODE_PRIVATE)
    }
}