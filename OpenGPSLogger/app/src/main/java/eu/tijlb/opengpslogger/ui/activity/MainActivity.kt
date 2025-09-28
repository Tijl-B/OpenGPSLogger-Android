package eu.tijlb.opengpslogger.ui.activity

import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import eu.tijlb.opengpslogger.BuildConfig
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.databinding.ActivityMainBinding
import eu.tijlb.opengpslogger.model.broadcast.LocationUpdateReceiver
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.database.locationbuffer.LocationBufferUtil
import eu.tijlb.opengpslogger.model.database.settings.TrackingStatusHelper
import eu.tijlb.opengpslogger.model.service.LocationNotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "ogl-mainactivity"

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationDbHelper: LocationDbHelper
    private lateinit var locationBufferUtil: LocationBufferUtil
    private lateinit var trackingStatusHelper: TrackingStatusHelper
    private lateinit var locationReceiver: LocationUpdateReceiver


    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Running MainActivity onCreate")
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        locationDbHelper = LocationDbHelper.getInstance(this.applicationContext)
        locationBufferUtil = LocationBufferUtil(this)
        trackingStatusHelper = TrackingStatusHelper(this)

        setContentView(binding.root)

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }

        binding.root.post {
            setupBottomNavigation()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            if (trackingStatusHelper.isActive()) {
                startPollingLocation()
            }

            locationBufferUtil.flushBuffer()
            locationDbHelper.updateDistAngleIfNeeded()
        }

        locationReceiver = LocationUpdateReceiver().apply {
            setOnLocationReceivedListener { _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    locationBufferUtil.flushBuffer()
                }
            }
        }

        registerLocationReceiver()
        Log.d(TAG, "Finished MainActivity onCreate")
    }

    override fun onNewIntent(intent: Intent?) {
        Log.d(TAG, "Running MainActivity onNewIntent")
        super.onNewIntent(intent)
    }

    override fun onStop() {
        Log.d(TAG, "Running MainActivity onStop")
        unregisterLocationReceiver()
        super.onStop()
    }

    override fun onRestart() {
        Log.d(TAG, "Running MainActivity onRestart")
        registerLocationReceiver()
        lifecycleScope.launch(Dispatchers.IO) {
            locationBufferUtil.flushBuffer()
        }
        super.onRestart()
    }

    override fun onDestroy() {
        Log.d(TAG, "Destroying MainActivity")
        unregisterLocationReceiver()
        super.onDestroy()
    }

    private fun setupBottomNavigation() {
        val navController = findNavController(R.id.nav_host_fragment)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        NavigationUI.setupWithNavController(bottomNav, navController)
    }

    private fun unregisterLocationReceiver() {
        try {
            unregisterReceiver(locationReceiver)
            Log.d(TAG, "Unregistered location receiver in MainActivity")
        } catch (e: IllegalArgumentException) {
            Log.i(TAG, "Failed to unregistered location receiver in MainActivity", e)
        }
    }

    private fun registerLocationReceiver() {
        val filter = IntentFilter("eu.tijlb.LOCATION_RECEIVED")
        this.registerReceiver(locationReceiver, filter, RECEIVER_NOT_EXPORTED)
        Log.d(TAG, "Registered location receiver in MainActivity")
    }

    private fun startPollingLocation() {
        Log.d(TAG, "Start polling location")
        val intent = Intent(this, LocationNotificationService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}