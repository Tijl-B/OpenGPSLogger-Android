package eu.tijlb.opengpslogger.model.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.database.settings.LocationRequestSettingsHelper
import eu.tijlb.opengpslogger.model.database.settings.TrackingStatusHelper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val STOP_SERVICE = "STOP_SERVICE"

class LocationNotificationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequestSettingsHelper: LocationRequestSettingsHelper
    private lateinit var trackingStatusHelper: TrackingStatusHelper
    private lateinit var presetChangedListener: SharedPreferences.OnSharedPreferenceChangeListener
    private lateinit var presetName: String

    private lateinit var notificationBuilder: NotificationCompat.Builder
    private var savedPoints = 0

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequestSettingsHelper = LocationRequestSettingsHelper(this)
        trackingStatusHelper = TrackingStatusHelper(this)

        presetChangedListener =
            locationRequestSettingsHelper.registerPresetChangedListener { updateLocationRequest() }

        setLocationRequest()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val location = locationResult.lastLocation
                if (location != null) {
                    Log.d("ogl-locationnotificationservice", "Location from service: $location")
                    val intent = Intent("eu.tijlb.LOCATION_UPDATE")
                    intent.putExtra("location", location)
                    sendBroadcast(intent)
                    Log.d("ogl-locationnotificationservice", "Broadcast location $location")
                    saveToDb(location)
                }
            }
        }
    }

    private fun updateLocationRequest() {
        Log.d("ogl-locationnotificationservice", "Updating location request")
        stopLocationUpdates()
        setLocationRequest()
        notificationBuilder = createNotificationBuilder()
        startForeground(1, notificationBuilder.build())
        startLocationUpdates()
    }

    private fun setLocationRequest() {
        val (preset, request) = locationRequestSettingsHelper.getTrackingSettings()
        locationRequest = request
        presetName = preset
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP_SERVICE) {
            Log.d("ogl-locationnotificationservice", "Stopping location polling from delete intent")
            stop()
            stopSelf()
            return START_NOT_STICKY
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("ogl-locationnotificationservice", "Permission not granted for notifications.")
            toast("Permission not granted for notifications, not tracking.")
            return START_NOT_STICKY
        }

        Log.d("ogl-locationnotificationservice", "Setting active to true")
        trackingStatusHelper.setActive(true)
        notificationBuilder = createNotificationBuilder()
        startForeground(1, notificationBuilder.build())

        startLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stop()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun stop() {
        locationRequestSettingsHelper.deregisterPresetChangedListener(presetChangedListener)
        Log.d("ogl-locationnotificationservice", "Setting active to false")
        trackingStatusHelper.setActive(false)
        stopLocationUpdates()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("ogl-locationnotificationservice", "Permission not granted for location updates.")
            toast("Permission not granted for location updates, not tracking.")
            return
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun toast(text: String) {
        Toast.makeText(
            this,
            text,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun createNotificationBuilder(): NotificationCompat.Builder {
        val notificationChannelId = "location_service_channel"

        val channel = NotificationChannel(
            notificationChannelId,
            "Location Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)

        val deleteIntent = Intent(this, LocationNotificationService::class.java)
            .apply { action = STOP_SERVICE }

        val deletePendingIntent =
            PendingIntent.getService(this, 0, deleteIntent, PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("OpenGPSLogger Location Service")
            .setContentText(getNotificationContent())
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setDeleteIntent(deletePendingIntent)

        return notificationBuilder
    }

    private fun saveToDb(location: Location) {
        val dbHelper = LocationDbHelper.getInstance(baseContext)
        dbHelper.save(location, "app::OpenGpsLogger")
        savedPoints++

        val formattedTime = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)


        notificationBuilder.setContentText(getNotificationContent())
        notificationBuilder.setStyle(
            NotificationCompat.BigTextStyle().bigText(
                getNotificationBigText(formattedTime)
            )
        )

        startForeground(1, notificationBuilder.build())
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