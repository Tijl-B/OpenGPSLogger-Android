package eu.tijlb.opengpslogger.model.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log

class LocationUpdateReceiver : BroadcastReceiver() {

    private var onLocationReceived: ((Location?) -> Unit)? = null

    fun setOnLocationReceivedListener(listener: (Location?) -> Unit) {
        onLocationReceived = listener
    }

    override fun onReceive(context: Context, intent: Intent) {
        val location = intent.getParcelableExtra("location", Location::class.java)
        Log.d("ogl-locationupdatereceiver", "Received location $location in LocationUpdateReceiver")
        onLocationReceived?.invoke(location)
    }
}