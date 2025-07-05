package eu.tijlb.opengpslogger.ui.activity

import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.databinding.ActivityMainBinding
import eu.tijlb.opengpslogger.model.broadcast.LocationUpdateReceiver
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.database.locationbuffer.LocationBufferUtil
import eu.tijlb.opengpslogger.model.database.settings.TrackingStatusHelper
import eu.tijlb.opengpslogger.model.service.LocationNotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationDbHelper: LocationDbHelper
    private lateinit var locationBufferUtil: LocationBufferUtil
    private lateinit var trackingStatusHelper: TrackingStatusHelper
    private lateinit var locationReceiver: LocationUpdateReceiver


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("ogl-mainactivity", "Running MainActivity onCreate")

        binding = ActivityMainBinding.inflate(layoutInflater)
        locationDbHelper = LocationDbHelper.getInstance(this)
        locationBufferUtil = LocationBufferUtil(this)
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
        locationBufferUtil.flushBufferAsync()

        locationReceiver = LocationUpdateReceiver().apply {
            setOnLocationReceivedListener { location ->
                locationBufferUtil.flushBufferAsync()
            }
        }

        registerLocationReceiver()
        Log.d("ogl-mainactivity", "Finished MainActivity onCreate")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onStop() {
        unregisterLocationReceiver()
        super.onStop()
    }

    override fun onRestart() {
        registerLocationReceiver()
        locationBufferUtil.flushBufferAsync()
        super.onRestart()
    }

    override fun onDestroy() {
        Log.d("ogl-mainactivity", "Destroying MainActivity")
        unregisterLocationReceiver()
        super.onDestroy()
    }

    private fun unregisterLocationReceiver() {
        try {
            unregisterReceiver(locationReceiver)
            Log.d("ogl-mainactivity", "Unregistered location receiver in MainActivity")
        } catch (e: IllegalArgumentException) {
            Log.i("ogl-mainactivity", "Failed to unregistered location receiver in MainActivity", e)
        }
    }

    private fun registerLocationReceiver() {
        val filter = IntentFilter("eu.tijlb.LOCATION_UPDATE")
        this.registerReceiver(locationReceiver, filter, RECEIVER_NOT_EXPORTED)
        Log.d("ogl-mainactivity", "Registered location receiver in MainActivity")
    }

    private fun startPollingLocation() {
        Log.d("ogl-mainactivity", "Start polling location")
        val intent = Intent(this, LocationNotificationService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}