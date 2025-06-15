package eu.tijlb.opengpslogger.ui.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.databinding.ActivityMainBinding
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.database.settings.TrackingStatusHelper
import eu.tijlb.opengpslogger.model.service.LocationNotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationDbHelper: LocationDbHelper
    private lateinit var trackingStatusHelper: TrackingStatusHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        locationDbHelper = LocationDbHelper.getInstance(this)
        trackingStatusHelper = TrackingStatusHelper(this)

        setContentView(binding.root)

        val navController = findNavController(R.id.nav_host_fragment_content_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNav.setOnItemSelectedListener { item ->
            run {
                when (item.itemId) {
                    R.id.item_browse -> navController.navigate(R.id.item_browse)
                    R.id.item_generate_image -> navController.navigate(R.id.item_generate_image)
                    R.id.item_manage_data -> navController.navigate(R.id.item_manage_data)
                    R.id.item_settings -> navController.navigate(R.id.item_settings)
                }
                return@run true
            }

        }
        CoroutineScope(Dispatchers.Default)
            .launch {
                if (trackingStatusHelper.isActive()) {
                    startPollingLocation()
                }
            }
        CoroutineScope(Dispatchers.IO)
            .launch {
                locationDbHelper.updateDistAngleIfNeeded()
            }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun startPollingLocation() {
        Log.d("ogl-mainactivity", "Start polling location")
        val intent = Intent(this, LocationNotificationService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}