package eu.tijlb.opengpslogger.model.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.model.database.locationbuffer.LocationBufferDbHelper
import eu.tijlb.opengpslogger.model.database.settings.LocationRequestSettingsHelper
import eu.tijlb.opengpslogger.model.database.settings.TrackingStatusHelper
import eu.tijlb.opengpslogger.ui.activity.MainActivity
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

private const val STOP_SERVICE = "STOP_SERVICE"
private const val TAG = "ogl-locationnotificationservice"

class LocationNotificationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequestSettingsHelper: LocationRequestSettingsHelper
    private lateinit var trackingStatusHelper: TrackingStatusHelper
    private lateinit var presetChangedListener: SharedPreferences.OnSharedPreferenceChangeListener
    private lateinit var locationBufferDbHelper: LocationBufferDbHelper

    private lateinit var notificationBuilder: NotificationCompat.Builder
    private var savedPoints = 0
    private var presetName = ""

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        Log.d(TAG, "Running onCreate of LocationNotificationService")
        super.onCreate()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        locationRequestSettingsHelper = LocationRequestSettingsHelper(applicationContext)
        trackingStatusHelper = TrackingStatusHelper(applicationContext)
        locationBufferDbHelper = LocationBufferDbHelper.getInstance(applicationContext)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                try {
                    locationResult.lastLocation
                        ?.let { saveLocation(it) }
                } catch (e: Throwable) {
                    Log.e(TAG, "Error while handling location result:", e)
                }
            }
        }

        serviceScope.launch {
            presetChangedListener =
                locationRequestSettingsHelper.registerPresetChangedListener { updateLocationRequest() }
        }
    }

    private fun saveLocation(location: Location): Job = serviceScope.launch {
        withTimeout(2.seconds) {
            Log.d(TAG, "Location from service: $location")
            saveToDb(location)
            withContext(Dispatchers.Main) {
                broadCastLocationReceived(location)
            }
        }
    }

    private fun updateLocationRequest() {
        serviceScope.launch {
            Log.d(TAG, "Updating location request of LocationNotificationService")
            stopLocationUpdates()
            notificationBuilder = createNotificationBuilder()
            withContext(Dispatchers.Main) { startForegroundNotification() }
            startLocationUpdates()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Running onStartCommand of LocationNotificationService")
        if (intent?.action == STOP_SERVICE) {
            Log.d(TAG, "Stopping location polling from delete intent")
            stop()
            stopSelf()
            return START_NOT_STICKY
        }

        notificationBuilder = createNotificationBuilder()
        startForegroundNotification()

        serviceScope.launch {
            if (ActivityCompat.checkSelfPermission(
                    this@LocationNotificationService,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Permission not granted for notifications.")
                withContext(Dispatchers.Main) {
                    toast("Permission not granted for notifications, not tracking.")
                }
                return@launch
            }

            Log.d(TAG, "Setting active to true")
            trackingStatusHelper.setActive(true)

            startLocationUpdates()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Running onDestroy of LocationNotificationService")
        super.onDestroy()
        stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stop() {
        locationRequestSettingsHelper.deregisterPresetChangedListener(presetChangedListener)
        Log.d(TAG, "Setting active to false")
        trackingStatusHelper.setActive(false)
        stopLocationUpdates()
        serviceScope.cancel()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Permission not granted for location updates.")
            toast("Permission not granted for location updates, not tracking.")
            return
        }

        stopLocationUpdates()
        val (preset, request) = locationRequestSettingsHelper.getTrackingSettings()
        presetName = preset
        fusedLocationClient.requestLocationUpdates(request, locationCallback, null)
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun broadCastLocationReceived(location: Location) {
        val intent = Intent("eu.tijlb.LOCATION_RECEIVED").apply {
            putExtra("location", location)
            setPackage(applicationContext.packageName)
        }
        Log.d(TAG, "Broadcast location $location")
        sendBroadcast(intent)
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun createNotificationBuilder(): NotificationCompat.Builder {
        val notificationChannelId = "location_service_channel"

        val channel = NotificationChannel(
            notificationChannelId,
            "Location Service",
            NotificationManager.IMPORTANCE_MIN
        )
        getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)

        val stopIntent = Intent(this, LocationNotificationService::class.java)
            .apply { action = STOP_SERVICE }
        val stopPendingIntent =
            PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getNotificationContent())
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(R.drawable.ic_notification, "Stop Tracking", stopPendingIntent)
    }

    private suspend fun saveToDb(location: Location) {
        withContext(Dispatchers.IO) {
            locationBufferDbHelper.save(location, "app::OpenGpsLogger")
            savedPoints++

            val formattedTime = Instant.ofEpochMilli(System.currentTimeMillis())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

            notificationBuilder.setContentText(getNotificationContent())
            notificationBuilder.setStyle(
                NotificationCompat.BigTextStyle().bigText(getNotificationBigText(formattedTime))
            )

            withContext(Dispatchers.Main) { startForegroundNotification() }
        }
    }

    private fun startForegroundNotification() {
        startForeground(
            1,
            notificationBuilder.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }

    private fun getNotificationContent() = getString(
        R.string.tracking_notification_content,
        savedPoints.toString(),
        presetName
    )

    private fun getNotificationBigText(formattedTime: String) = getString(
        R.string.tracking_notification_bigtext,
        savedPoints.toString(),
        presetName,
        formattedTime
    )
}
