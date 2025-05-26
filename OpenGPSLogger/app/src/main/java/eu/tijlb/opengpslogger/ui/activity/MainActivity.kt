package eu.tijlb.opengpslogger.ui.activity

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.switchmaterial.SwitchMaterial
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.databinding.ActivityMainBinding
import eu.tijlb.opengpslogger.model.database.densitymap.continent.ContinentDensityMapDbHelper
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.database.settings.AdvancedFiltersHelper
import eu.tijlb.opengpslogger.model.database.settings.ColorMode
import eu.tijlb.opengpslogger.model.database.settings.LocationRequestSettingsHelper
import eu.tijlb.opengpslogger.model.database.settings.PRESET_HIGH
import eu.tijlb.opengpslogger.model.database.settings.PRESET_HIGHEST
import eu.tijlb.opengpslogger.model.database.settings.PRESET_LOW
import eu.tijlb.opengpslogger.model.database.settings.PRESET_MEDIUM
import eu.tijlb.opengpslogger.model.database.settings.PRESET_PASSIVE
import eu.tijlb.opengpslogger.model.database.settings.VisualisationSettingsHelper
import eu.tijlb.opengpslogger.model.database.tileserver.TileServerDbHelper
import eu.tijlb.opengpslogger.model.dto.VisualisationSettingsDto
import eu.tijlb.opengpslogger.model.util.DensityMapUtil
import eu.tijlb.opengpslogger.ui.singleton.ImageRendererViewSingleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var locationRequestSettingsHelper: LocationRequestSettingsHelper
    private lateinit var advancedFiltersHelper: AdvancedFiltersHelper
    private lateinit var visualisationSettingsHelper: VisualisationSettingsHelper
    private lateinit var locationDbHelper: LocationDbHelper
    private lateinit var tileServerDbHelper: TileServerDbHelper
    private lateinit var continentDensityMapDbHelper: ContinentDensityMapDbHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        locationRequestSettingsHelper = LocationRequestSettingsHelper(this)
        advancedFiltersHelper = AdvancedFiltersHelper(this)
        visualisationSettingsHelper = VisualisationSettingsHelper(this)
        locationDbHelper = LocationDbHelper.getInstance(this)
        tileServerDbHelper = TileServerDbHelper(this)
        continentDensityMapDbHelper = ContinentDensityMapDbHelper(this)

        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        CoroutineScope(Dispatchers.IO)
            .launch {
                locationDbHelper.updateDistAngleIfNeeded()
            }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> openAboutDialog()
            R.id.action_trackingSettings -> openTrackingSettingsDialog()
            R.id.action_advancedFilters -> openAdvancedFiltersDialog()
            R.id.action_visualisationSettings -> openVisualisationSettingsDialog()
            R.id.action_mapSettings -> openMapSettingsDialog()
            R.id.action_densityMapSettings -> openDensityMapSettingsDialog()
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun openDensityMapSettingsDialog(): Boolean {
        val dialogView = layoutInflater.inflate(R.layout.dialog_density_map_settings, null)
        val button = dialogView.findViewById<Button>(R.id.btn_recalculate_density_map)

        button.setOnClickListener {
            Log.d("ogl-mainactivity", "Recalculating density map")
            DensityMapUtil.recreateDatabaseAsync(continentDensityMapDbHelper, locationDbHelper)
            button.setBackgroundColor(Color.GRAY)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.density_map_settings))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.density_map_settings_confirm)) { dialog, _ ->
                Log.d("ogl-mainactivity", "Confirmed density map settings dialog")
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

        AlertDialog.Builder(this)
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
            this,
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
        val densityMapSwitch = dialogView.findViewById<SwitchMaterial>(R.id.switch_enable_density_map)
        val colorSpinner = dialogView.findViewById<Spinner>(R.id.spinner_color)

        val colorModeValues = ColorMode.entries
        val spinnerValues = colorModeValues.map {
            when(it) {
                ColorMode.SINGLE_COLOR -> "Single color"
                ColorMode.MULTI_COLOR_YEAR -> "Multi color (1 year)"
                ColorMode.MULTI_COLOR_MONTH -> "Multi color (30 days)"
                ColorMode.MULTI_COLOR_DAY -> "Multi color (1 day)"
                ColorMode.MULTI_COLOR_HOUR -> "Multi color (1 hour)"
            }
        }

        val adapter = ArrayAdapter(
            this,
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

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.visualisation_settings_title))
            .setView(dialogView)
            .setPositiveButton(R.string.visualisation_settings_confirm) { dialog, _ ->
                val dotSize = dotSizeEditText.text.toString().toFloatOrNull()
                val lineSize = lineSizeEditText.text.toString().toFloatOrNull()
                val colorSeed = colorSeedEditText.text.toString().toIntOrNull()?:0
                val opacity = opacityEditText.text.toString().toIntOrNull()?.coerceIn(1, 100)?:100
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

        AlertDialog.Builder(this)
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
            this,
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

        AlertDialog.Builder(this)
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
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName

        textViewVersion.text = versionName

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.about_title))
            .setView(dialogView)
            .setPositiveButton(R.string.about_confirm) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
        return true
    }
}