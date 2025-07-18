package eu.tijlb.opengpslogger.model.database.settings

import android.content.Context
import android.content.SharedPreferences
import android.location.LocationRequest.PASSIVE_INTERVAL
import android.util.Log
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import java.util.concurrent.TimeUnit

private const val LOCATION_REQUEST = "LOCATION_REQUEST"

private const val ACCURACY = "ACCURACY"
private const val INTERVAL_MILLIS = "INTERVAL_MILLIS"
private const val MIN_UPDATE_INTERVAL_MILLIS = "MIN_UPDATE_INTERVAL_MILLIS"
private const val MAX_UPDATE_AGE_MILLIS = "MAX_UPDATE_AGE_MILLIS"
private const val MIN_UPDATE_DISTANCE_METERS = "MIN_UPDATE_DISTANCE_METERS"
private const val WAIT_FOR_ACCURATE_LOCATION = "WAIT_FOR_ACCURATE_LOCATION"
private const val PRESET = "PRESET"

const val PRESET_HIGHEST = "HIGHEST"
const val PRESET_HIGH = "HIGH"
const val PRESET_MEDIUM = "MEDIUM"
const val PRESET_LOW = "LOW"
const val PRESET_PASSIVE = "PASSIVE"

private const val TAG = "ogl-locationrequestsettingshelper"

class LocationRequestSettingsHelper(val context: Context) {

    private val highestPresetAccuracy = Priority.PRIORITY_HIGH_ACCURACY
    private val highestPresetIntervalMillis = TimeUnit.SECONDS.toMillis(3)
    private val highestPresetMinUpdateIntervalMillis = TimeUnit.SECONDS.toMillis(1)
    private val highestPresetMaxUpdateAgeMillis = TimeUnit.SECONDS.toMillis(5)
    private val highestPresetMinUpdateDistanceMeters = 5F
    private val highestPresetWaitForAccurateLocation = true

    private val highPresetAccuracy = Priority.PRIORITY_HIGH_ACCURACY
    private val highPresetIntervalMillis = TimeUnit.SECONDS.toMillis(15)
    private val highPresetMinUpdateIntervalMillis = TimeUnit.SECONDS.toMillis(1)
    private val highPresetMaxUpdateAgeMillis = TimeUnit.SECONDS.toMillis(10)
    private val highPresetMinUpdateDistanceMeters = 10F
    private val highPresetWaitForAccurateLocation = true

    private val mediumPresetAccuracy = Priority.PRIORITY_HIGH_ACCURACY
    private val mediumPresetIntervalMillis = TimeUnit.MINUTES.toMillis(1)
    private val mediumPresetMinUpdateIntervalMillis = TimeUnit.SECONDS.toMillis(1)
    private val mediumPresetMaxUpdateAgeMillis = TimeUnit.SECONDS.toMillis(50)
    private val mediumPresetMinUpdateDistanceMeters = 20F
    private val mediumPresetWaitForAccurateLocation = true

    private val lowPresetAccuracy = Priority.PRIORITY_BALANCED_POWER_ACCURACY
    private val lowPresetIntervalMillis = TimeUnit.MINUTES.toMillis(3)
    private val lowPresetMinUpdateIntervalMillis = TimeUnit.SECONDS.toMillis(1)
    private val lowPresetMaxUpdateAgeMillis = TimeUnit.MINUTES.toMillis(2)
    private val lowPresetMinUpdateDistanceMeters = 50F
    private val lowPresetWaitForAccurateLocation = false

    private val passivePresetAccuracy = Priority.PRIORITY_PASSIVE
    private val passivePresetIntervalMillis = PASSIVE_INTERVAL
    private val passivePresetMinUpdateIntervalMillis = TimeUnit.SECONDS.toMillis(1)
    private val passivePresetMaxUpdateAgeMillis = TimeUnit.MINUTES.toMillis(15)
    private val passivePresetMinUpdateDistanceMeters = 50F
    private val passivePresetWaitForAccurateLocation = false

    fun getTrackingSettings(): Pair<String, LocationRequest> {
        val locationRequestPreferences = getLocationRequestPreferences()
        val preset = locationRequestPreferences.getString(PRESET, PRESET_MEDIUM)!!
        val accuracy = locationRequestPreferences.getInt(ACCURACY, mediumPresetAccuracy)
        val intervalMillis =
            locationRequestPreferences.getLong(INTERVAL_MILLIS, mediumPresetIntervalMillis)
        val minUpdateIntervalMillis = locationRequestPreferences.getLong(
            MIN_UPDATE_INTERVAL_MILLIS,
            mediumPresetMinUpdateIntervalMillis
        )
        val maxUpdateAgeMillis = locationRequestPreferences.getLong(
            MAX_UPDATE_AGE_MILLIS,
            mediumPresetMaxUpdateAgeMillis
        )
        val minUpdateDistanceMeters = locationRequestPreferences.getFloat(
            MIN_UPDATE_DISTANCE_METERS,
            mediumPresetMinUpdateDistanceMeters
        )
        val waitForAccurateLocation = locationRequestPreferences.getBoolean(
            WAIT_FOR_ACCURATE_LOCATION,
            mediumPresetWaitForAccurateLocation
        )

        val locationRequest = LocationRequest.Builder(accuracy, intervalMillis)
            .setMinUpdateIntervalMillis(minUpdateIntervalMillis)
            .setMaxUpdateAgeMillis(maxUpdateAgeMillis)
            .setMinUpdateDistanceMeters(minUpdateDistanceMeters)
            .setWaitForAccurateLocation(waitForAccurateLocation)
            .build()
        return Pair(preset, locationRequest)
    }

    fun getSelectedPreset(): String? {
        val locationRequestPreferences = getLocationRequestPreferences()
        return locationRequestPreferences.getString(PRESET, PRESET_MEDIUM)
    }

    fun setPresetTrackingSettings(preset: String) {
        when (preset) {
            PRESET_HIGHEST -> setHighestPreset()
            PRESET_HIGH -> setHighPreset()
            PRESET_MEDIUM -> setMediumPreset()
            PRESET_LOW -> setLowPreset()
            PRESET_PASSIVE -> setPassivePreset()
            else -> Log.w(
                TAG,
                "Unknown preset $preset, not updating settings."
            )
        }
    }

    fun registerPresetChangedListener(function: (String) -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        Log.d(TAG, "Registering preset changed listener")
        val preferences = getLocationRequestPreferences()

        val preferencesListener =
            SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                Log.d(
                    TAG,
                    "SharedPreferences.OnSharedPreferenceChangeListener called for key $key"
                )
                if (key == PRESET) {
                    sharedPreferences.getString(PRESET, PRESET_MEDIUM)
                        ?.let { function(it) }
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(preferencesListener)
        return preferencesListener
    }

    fun deregisterPresetChangedListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val preferences = getLocationRequestPreferences()
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun setHighestPreset() {
        val locationRequestPreferences = getLocationRequestPreferences()
        with(locationRequestPreferences.edit()) {
            putString(PRESET, PRESET_HIGHEST)
            putInt(ACCURACY, highestPresetAccuracy)
            putLong(INTERVAL_MILLIS, highestPresetIntervalMillis)
            putLong(MIN_UPDATE_INTERVAL_MILLIS, highestPresetMinUpdateIntervalMillis)
            putLong(MAX_UPDATE_AGE_MILLIS, highestPresetMaxUpdateAgeMillis)
            putFloat(MIN_UPDATE_DISTANCE_METERS, highestPresetMinUpdateDistanceMeters)
            putBoolean(WAIT_FOR_ACCURATE_LOCATION, highestPresetWaitForAccurateLocation)
            apply()
        }
    }

    private fun setHighPreset() {
        val locationRequestPreferences = getLocationRequestPreferences()
        with(locationRequestPreferences.edit()) {
            putString(PRESET, PRESET_HIGH)
            putInt(ACCURACY, highPresetAccuracy)
            putLong(INTERVAL_MILLIS, highPresetIntervalMillis)
            putLong(MIN_UPDATE_INTERVAL_MILLIS, highPresetMinUpdateIntervalMillis)
            putLong(MAX_UPDATE_AGE_MILLIS, highPresetMaxUpdateAgeMillis)
            putFloat(MIN_UPDATE_DISTANCE_METERS, highPresetMinUpdateDistanceMeters)
            putBoolean(WAIT_FOR_ACCURATE_LOCATION, highPresetWaitForAccurateLocation)
            apply()
        }
    }

    private fun setMediumPreset() {
        val locationRequestPreferences = getLocationRequestPreferences()
        with(locationRequestPreferences.edit()) {
            putString(PRESET, PRESET_MEDIUM)
            putInt(ACCURACY, mediumPresetAccuracy)
            putLong(INTERVAL_MILLIS, mediumPresetIntervalMillis)
            putLong(MIN_UPDATE_INTERVAL_MILLIS, mediumPresetMinUpdateIntervalMillis)
            putLong(MAX_UPDATE_AGE_MILLIS, mediumPresetMaxUpdateAgeMillis)
            putFloat(MIN_UPDATE_DISTANCE_METERS, mediumPresetMinUpdateDistanceMeters)
            putBoolean(WAIT_FOR_ACCURATE_LOCATION, mediumPresetWaitForAccurateLocation)
            apply()
        }
    }

    private fun setLowPreset() {
        val locationRequestPreferences = getLocationRequestPreferences()
        with(locationRequestPreferences.edit()) {
            putString(PRESET, PRESET_LOW)
            putInt(ACCURACY, lowPresetAccuracy)
            putLong(INTERVAL_MILLIS, lowPresetIntervalMillis)
            putLong(MIN_UPDATE_INTERVAL_MILLIS, lowPresetMinUpdateIntervalMillis)
            putLong(MAX_UPDATE_AGE_MILLIS, lowPresetMaxUpdateAgeMillis)
            putFloat(MIN_UPDATE_DISTANCE_METERS, lowPresetMinUpdateDistanceMeters)
            putBoolean(WAIT_FOR_ACCURATE_LOCATION, lowPresetWaitForAccurateLocation)
            apply()
        }
    }

    private fun setPassivePreset() {
        val locationRequestPreferences = getLocationRequestPreferences()
        with(locationRequestPreferences.edit()) {
            putString(PRESET, PRESET_PASSIVE)
            putInt(ACCURACY, passivePresetAccuracy)
            putLong(INTERVAL_MILLIS, passivePresetIntervalMillis)
            putLong(MIN_UPDATE_INTERVAL_MILLIS, passivePresetMinUpdateIntervalMillis)
            putLong(MAX_UPDATE_AGE_MILLIS, passivePresetMaxUpdateAgeMillis)
            putFloat(MIN_UPDATE_DISTANCE_METERS, passivePresetMinUpdateDistanceMeters)
            putBoolean(WAIT_FOR_ACCURATE_LOCATION, passivePresetWaitForAccurateLocation)
            apply()
        }
    }

    private fun getLocationRequestPreferences(): SharedPreferences {
        Log.d(TAG, "Getting shared preferences of $LOCATION_REQUEST")
        return context.getSharedPreferences(LOCATION_REQUEST, Context.MODE_PRIVATE)
    }
}
