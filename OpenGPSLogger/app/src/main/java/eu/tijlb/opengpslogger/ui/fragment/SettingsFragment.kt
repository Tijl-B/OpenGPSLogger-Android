package eu.tijlb.opengpslogger.ui.fragment;

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.model.database.densitymap.DensityMapAdapter
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.database.settings.AdvancedFiltersHelper
import eu.tijlb.opengpslogger.model.database.settings.ColorMode
import eu.tijlb.opengpslogger.model.database.settings.LocationRequestSettingsHelper
import eu.tijlb.opengpslogger.model.database.settings.PRESET_HIGH
import eu.tijlb.opengpslogger.model.database.settings.PRESET_HIGHEST
import eu.tijlb.opengpslogger.model.database.settings.PRESET_LOW
import eu.tijlb.opengpslogger.model.database.settings.PRESET_MEDIUM
import eu.tijlb.opengpslogger.model.database.settings.PRESET_PASSIVE
import eu.tijlb.opengpslogger.model.database.settings.TrackingStatusHelper
import eu.tijlb.opengpslogger.model.database.settings.VisualisationSettingsHelper
import eu.tijlb.opengpslogger.model.database.tileserver.TileServerDbHelper
import eu.tijlb.opengpslogger.model.dto.VisualisationSettingsDto
import eu.tijlb.opengpslogger.model.service.LocationNotificationService
import eu.tijlb.opengpslogger.model.util.DensityMapUtil
import eu.tijlb.opengpslogger.ui.singleton.ImageRendererViewSingleton

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var locationRequestSettingsHelper: LocationRequestSettingsHelper
    private lateinit var advancedFiltersHelper: AdvancedFiltersHelper
    private lateinit var visualisationSettingsHelper: VisualisationSettingsHelper
    private lateinit var densityMapAdapter: DensityMapAdapter
    private lateinit var tileServerDbHelper: TileServerDbHelper
    private lateinit var locationDbHelper: LocationDbHelper
    private lateinit var context: Context

    private lateinit var requestLocationButton: Button
    private lateinit var trackingStatusHelper: TrackingStatusHelper
    private lateinit var trackingActiveChangedListener: OnSharedPreferenceChangeListener
    private var requestingLocation = false
        set(value) {
            field = value
            updateRequestLocationButton()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("ogl-settingsfragment", "Start creating settings fragment")
        context = requireContext()
        locationRequestSettingsHelper = LocationRequestSettingsHelper(context)
        advancedFiltersHelper = AdvancedFiltersHelper(context)
        visualisationSettingsHelper = VisualisationSettingsHelper(context)
        densityMapAdapter = DensityMapAdapter(context)
        tileServerDbHelper = TileServerDbHelper.getInstance(context)
        locationDbHelper = LocationDbHelper.getInstance(context)
        trackingStatusHelper = TrackingStatusHelper(context)
        Log.d("ogl-settingsfragment", "Done creating settings fragment")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("ogl-settingsfragment", "Start creating settings fragment view")
        requestLocationButton = view.findViewById<Button>(R.id.button_request_location)
        requestLocationButton.setOnClickListener { toggleLocationTracking() }

        view.findViewById<Button>(R.id.button_about)
            .setOnClickListener { openAboutDialog() }
        view.findViewById<Button>(R.id.button_tracking_settings)
            .setOnClickListener { openTrackingSettingsDialog() }
        view.findViewById<Button>(R.id.button_advanced_filters)
            .setOnClickListener { openAdvancedFiltersDialog() }
        view.findViewById<Button>(R.id.button_visualisation_settings)
            .setOnClickListener { openVisualisationSettingsDialog() }
        view.findViewById<Button>(R.id.button_map_settings)
            .setOnClickListener { openMapSettingsDialog() }
        view.findViewById<Button>(R.id.button_density_map_settings)
            .setOnClickListener { openDensityMapSettingsDialog() }

        requestingLocation = trackingStatusHelper.isActive()
        trackingActiveChangedListener = trackingStatusHelper.registerActiveChangedListener {
            requestingLocation = it
        }
        Log.d("ogl-settingsfragment", "Done creating settings fragment view")
    }

    override fun onDestroyView() {
        Log.d("ogl-settingsfragment", "Destroying SettingsFragment")
        trackingStatusHelper.deregisterActiveChangedListener(trackingActiveChangedListener)
        super.onDestroyView()
    }

    private fun toggleLocationTracking() {
        requestLocationButton.text = getString(R.string.starting_tracking)
        if (checkAndRequestPermission(Manifest.permission.ACCESS_FINE_LOCATION, 101)) {
            requestLocationButton.text = getString(R.string.location_permission_missing)
            return
        }
        if (checkAndRequestPermission(Manifest.permission.POST_NOTIFICATIONS, 102)) {
            requestLocationButton.text = getString(R.string.notification_permission_missing)
            return
        }

        if (!requestingLocation) {
            startPollingLocation()
        } else {
            stopPollingLocation()
        }

    }

    private fun openDensityMapSettingsDialog(): Boolean {
        val dialogView = layoutInflater.inflate(R.layout.dialog_density_map_settings, null)
        val button = dialogView.findViewById<Button>(R.id.btn_recalculate_density_map)

        button.setOnClickListener {
            Log.d("ogl-settingsfragment", "Recalculating density map")
            DensityMapUtil.recreateDatabaseAsync(densityMapAdapter, locationDbHelper)
            button.setBackgroundColor(Color.GRAY)
        }
        AlertDialog.Builder(context)
            .setTitle(getString(R.string.density_map_settings))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.density_map_settings_confirm)) { dialog, _ ->
                Log.d("ogl-settingsfragment", "Confirmed density map settings dialog")
            }
            .create()
            .show()
        return true
    }

    private fun openMapSettingsDialog(): Boolean {
        val dialogView = layoutInflater.inflate(R.layout.dialog_map_settings, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinner_tileServer)
        val nameEditText = dialogView.findViewById<EditText>(R.id.editText_tileServerName)
        val urlEditText = dialogView.findViewById<EditText>(R.id.editText_tileServerUrl)
        val copyrightEditText = dialogView.findViewById<EditText>(R.id.editText_copyrightWatermark)
        val deleteButton = dialogView.findViewById<ImageButton>(R.id.imagebutton_deleteTileServer)

        setTileServerSpinner(deleteButton, spinner)

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.tile_server_settings_title))
            .setView(dialogView)
            .setPositiveButton(R.string.tile_server_settings_confirm) { dialog, _ ->
                val selection = spinner.selectedItem.toString()
                val nameValue = nameEditText.text.toString()
                val urlValue = urlEditText.text.toString()
                val copyrightValue = copyrightEditText.text.toString()
                Log.d(
                    "ogl-mainactivity",
                    "Got tile server selection $selection, name $nameValue and url $urlValue"
                )
                if (nameValue.isNotEmpty() && urlValue.startsWith("https://")) {
                    tileServerDbHelper.save(nameValue, urlValue, copyrightValue)
                    tileServerDbHelper.setActive(nameValue)
                } else {
                    tileServerDbHelper.setActive(selection)
                }
                ImageRendererViewSingleton.redrawOsm()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.tile_server_settings_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
        return true
    }

    private fun setTileServerSpinner(deleteButton: ImageButton, spinner: Spinner) {
        val names = tileServerDbHelper.getNames()
        if (names.size > 1) {
            deleteButton.visibility = View.VISIBLE
            deleteButton.setOnClickListener {
                deleteButton.visibility = View.INVISIBLE
                val selection = spinner.selectedItem.toString()
                tileServerDbHelper.delete(selection)
                setTileServerSpinner(deleteButton, spinner)
            }
        }

        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            names
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun openVisualisationSettingsDialog(): Boolean {
        val dialogView = layoutInflater.inflate(R.layout.dialog_visualisation_settings, null)
        val dotSizeEditText = dialogView.findViewById<EditText>(R.id.editText_dotSize)
        val lineSizeEditText = dialogView.findViewById<EditText>(R.id.editText_lineSize)
        val lineMaxMinsDeltaEditText =
            dialogView.findViewById<EditText>(R.id.editText_connectLinesMaxTimeDelta)
        val colorSeedEditText = dialogView.findViewById<EditText>(R.id.editText_colorSeed)
        val opacityEditText = dialogView.findViewById<EditText>(R.id.editText_opacity)
        val lineSwitch = dialogView.findViewById<SwitchMaterial>(R.id.switch_enable_lines)
        val densityMapSwitch =
            dialogView.findViewById<SwitchMaterial>(R.id.switch_enable_density_map)
        val colorSpinner = dialogView.findViewById<Spinner>(R.id.spinner_color)

        val colorModeValues = ColorMode.entries
        val spinnerValues = colorModeValues.map {
            when (it) {
                ColorMode.SINGLE_COLOR -> getString(R.string.single_color)
                ColorMode.MULTI_COLOR_YEAR -> getString(R.string.multi_color_1_year)
                ColorMode.MULTI_COLOR_MONTH -> getString(R.string.multi_color_30_days)
                ColorMode.MULTI_COLOR_DAY -> getString(R.string.multi_color_1_day)
                ColorMode.MULTI_COLOR_HOUR -> getString(R.string.multi_color_1_hour)
            }
        }

        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            spinnerValues
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        colorSpinner.adapter = adapter

        val settings = visualisationSettingsHelper.getVisualisationSettings()
        colorSpinner.setSelection(colorModeValues.indexOf(settings.colorMode))
        dotSizeEditText.setText(settings.dotSize?.toString() ?: getString(R.string.auto))
        lineSizeEditText.setText(settings.lineSize?.toString() ?: getString(R.string.auto))
        colorSeedEditText.setText(settings.colorSeed.toString())
        opacityEditText.setText(settings.opacityPercentage.toString())
        lineMaxMinsDeltaEditText.setText(settings.connectLinesMaxMinutesDelta.toString())
        lineSwitch.isChecked = settings.drawLines
        densityMapSwitch.isChecked = settings.drawDensityMap

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.visualisation_settings_title))
            .setView(dialogView)
            .setPositiveButton(R.string.visualisation_settings_confirm) { dialog, _ ->
                val dotSize = dotSizeEditText.text.toString().toFloatOrNull()
                val lineSize = lineSizeEditText.text.toString().toFloatOrNull()
                val colorSeed = colorSeedEditText.text.toString().toIntOrNull() ?: 0
                val opacity = opacityEditText.text.toString().toIntOrNull()?.coerceIn(1, 100) ?: 100
                val lineMaxMinsDelta = lineMaxMinsDeltaEditText.text.toString().toLongOrNull()
                    ?: settings.connectLinesMaxMinutesDelta
                val drawLines = lineSwitch.isChecked
                val drawDensityMap = densityMapSwitch.isChecked
                val settingsDto = VisualisationSettingsDto(
                    drawLines,
                    drawDensityMap,
                    lineSize,
                    dotSize,
                    lineMaxMinsDelta,
                    colorModeValues[colorSpinner.selectedItemPosition],
                    colorSeed,
                    opacity
                )
                visualisationSettingsHelper.setVisualisationSettings(settingsDto)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.visualisation_settings_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
        return true
    }

    private fun openAdvancedFiltersDialog(): Boolean {
        val dialogView = layoutInflater.inflate(R.layout.dialog_advanced_filters, null)
        val minAccuracyEditText = dialogView.findViewById<EditText>(R.id.editText_minAccuracy)
        val minAngleEditText = dialogView.findViewById<EditText>(R.id.editText_minAngle)

        var minAccuracy = advancedFiltersHelper.getMinAccuracy()
        minAccuracyEditText.setText(minAccuracy?.toString() ?: "")

        var minAngle: Float? = advancedFiltersHelper.getMinAngle()
        minAngleEditText.setText(minAngle.toString())

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.advanced_filters_title))
            .setView(dialogView)
            .setPositiveButton(R.string.advanced_filters_confirm) { dialog, _ ->
                minAccuracy = minAccuracyEditText.text.toString().toFloatOrNull()
                minAngle = minAngleEditText.text.toString().toFloatOrNull()

                advancedFiltersHelper.setMinAccuracy(minAccuracy)
                advancedFiltersHelper.setMinAngle(minAngle)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.advanced_filters_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
        return true
    }

    private fun openTrackingSettingsDialog(): Boolean {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tracking_settings, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinner_presets)
        val presets = listOf(PRESET_HIGHEST, PRESET_HIGH, PRESET_MEDIUM, PRESET_LOW, PRESET_PASSIVE)
        val presetsText = presets.map {
            when (it) {
                PRESET_HIGHEST -> getString(R.string.preset_highest)
                PRESET_HIGH -> getString(R.string.preset_high)
                PRESET_MEDIUM -> getString(R.string.preset_medium)
                PRESET_LOW -> getString(R.string.preset_low)
                PRESET_PASSIVE -> getString(R.string.preset_passive)
                else -> it
            }
        }
        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            presetsText
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val selectedPreset = locationRequestSettingsHelper.getSelectedPreset()
        selectedPreset
            ?.let { presets.indexOf(it) }
            ?.takeIf { it >= 0 }
            ?.let { spinner.setSelection(it) }

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.tracking_settings_title))
            .setView(dialogView)
            .setPositiveButton(R.string.tracking_settings_confirm) { dialog, _ ->
                val selection = presets[spinner.selectedItemPosition]
                locationRequestSettingsHelper.setPresetTrackingSettings(selection)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.tracking_settings_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
        return true
    }

    private fun openAboutDialog(): Boolean {
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)

        val textViewVersion = dialogView.findViewById<TextView>(R.id.textView_versionNumber)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName

        textViewVersion.text = versionName

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.about_title))
            .setView(dialogView)
            .setPositiveButton(R.string.about_confirm) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
        return true
    }

    private fun updateRequestLocationButton() {
        if (requestingLocation) {
            requestLocationButton.text = getString(R.string.stop_tracking)
        } else {
            requestLocationButton.text = getString(R.string.start_tracking)
        }
    }

    private fun startPollingLocation() {
        Log.d("ogl-settingsfragment", "Start polling location")
        val intent = Intent(requireContext(), LocationNotificationService::class.java)
        ContextCompat.startForegroundService(requireContext(), intent)

        requestingLocation = true
    }

    private fun stopPollingLocation() {
        Log.d("ogl-settingsfragment", "Stop polling location")
        val stopIntent = Intent(requireContext(), LocationNotificationService::class.java)
        requireContext().stopService(stopIntent)

        requestingLocation = false
    }

    private fun checkAndRequestPermission(permission: String, requestCode: Int): Boolean {
        if (!hasPermission(permission)) {
            requestPermission(permission, requestCode)
            return true
        }
        return false
    }

    private fun requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(permission),
            requestCode
        )
    }

    private fun hasPermission(permission: String) = ActivityCompat.checkSelfPermission(
        requireContext(),
        permission,
    ) == PackageManager.PERMISSION_GRANTED

}
