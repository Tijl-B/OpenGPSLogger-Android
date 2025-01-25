package eu.tijlb.opengpslogger.activity

import android.app.AlertDialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.database.settings.AdvancedFiltersHelper
import eu.tijlb.opengpslogger.database.settings.PRESET_HIGH
import eu.tijlb.opengpslogger.database.settings.PRESET_HIGHEST
import eu.tijlb.opengpslogger.database.settings.PRESET_LOW
import eu.tijlb.opengpslogger.database.settings.PRESET_MEDIUM
import eu.tijlb.opengpslogger.database.settings.PRESET_PASSIVE
import eu.tijlb.opengpslogger.database.settings.LocationRequestSettingsHelper
import eu.tijlb.opengpslogger.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var locationRequestSettingsHelper: LocationRequestSettingsHelper
    private lateinit var advancedFiltersHelper: AdvancedFiltersHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        locationRequestSettingsHelper = LocationRequestSettingsHelper(this)
        advancedFiltersHelper = AdvancedFiltersHelper(this)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_about -> openAboutDialog()
            R.id.action_trackingSettings -> openTrackingSettingsDialog()
            R.id.action_advancedFilters -> openAdvancedFiltersDialog()
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    private fun openAdvancedFiltersDialog(): Boolean {
        val dialogView = layoutInflater.inflate(R.layout.dialog_advanced_filters, null)
        val minAccuracyEditText = dialogView.findViewById<EditText>(R.id.editText_minAccuracy)

        var minAccuracy = advancedFiltersHelper.getMinAccuracy()
        minAccuracyEditText.setText(minAccuracy?.toString()?:"")

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.tracking_settings_title))
            .setView(dialogView)
            .setPositiveButton(R.string.tracking_settings_confirm) { dialog, _ ->
                minAccuracy = minAccuracyEditText.text.toString().toFloatOrNull()
                advancedFiltersHelper.setMinAccuracy(minAccuracy)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.tracking_settings_cancel) { dialog, _ ->
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