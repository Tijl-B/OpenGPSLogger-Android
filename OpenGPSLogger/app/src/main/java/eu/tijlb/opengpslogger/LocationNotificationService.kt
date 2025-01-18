package eu.tijlb.opengpslogger

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
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
import com.google.android.gms.location.Priority
import eu.tijlb.opengpslogger.database.location.LocationDbHelper
import java.util.concurrent.TimeUnit

class LocationNotificationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private lateinit var notificationBuilder: NotificationCompat.Builder
    private var savedPoints = 0

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TimeUnit.MINUTES.toMillis(2))
                .setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(5))
                .setMaxUpdateAgeMillis(TimeUnit.SECONDS.toMillis(100))
                .setMinUpdateDistanceMeters(35F)
                .setWaitForAccurateLocation(true)
                .build()

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("LocationService", "Permission not granted for notifications.")
            toast("Permission not granted for notifications, not tracking.",)
            return START_NOT_STICKY
        }

        notificationBuilder = createNotificationBuilder()
        startForeground(1, notificationBuilder.build())

        startLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("LocationService", "Permission not granted for location updates.")
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

        // Create notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Location Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Location Service")
            .setContentText("Tracked 0 points this session.")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        return notificationBuilder
    }

    private fun saveToDb(location: Location) {
        // TODO put in separate thread or listener?
        val dbHelper = LocationDbHelper.getInstance(baseContext)
        dbHelper.save(location, "app::OpenGpsLogger")
        notificationBuilder.setContentText("Tracked ${++savedPoints} points this session.")
        startForeground(1, notificationBuilder.build());
    }
}