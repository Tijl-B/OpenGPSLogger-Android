package eu.tijlb.opengpslogger.ui.fragment

import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.model.broadcast.LocationUpdateReceiver
import eu.tijlb.opengpslogger.ui.view.LayeredMapView

private const val TAG = "ogl-browsefragment"

class BrowseFragment : Fragment(R.layout.fragment_browse) {

    private lateinit var layeredMapView: LayeredMapView

    private var locationReceiver: LocationUpdateReceiver = LocationUpdateReceiver().apply {
        setOnLocationReceivedListener {
            Log.d(TAG, "Got new location $it")
            layeredMapView.redraw()
        }
    }

    init {
        registerLocationReceiver()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "Creating browse fragment view")
        super.onViewCreated(view, savedInstanceState)

        layeredMapView = view.findViewById(R.id.layeredMapView)
    }

    override fun onPause() {
        Log.d(TAG, "Browse fragment onPause")
        layeredMapView.stopUpdates()
        unregisterLocationReceiver()
        super.onPause()
    }

    override fun onResume() {
        Log.d(TAG, "Browse fragment onResume")
        registerLocationReceiver()
        super.onResume()
    }

    private fun unregisterLocationReceiver() {
        try {
            context?.unregisterReceiver(locationReceiver)
            Log.d(TAG, "Unregistered location receiver in LastLocationBitmapRenderer")
        } catch (e: IllegalArgumentException) {
            Log.i(TAG, "Failed to unregistered location receiver in LastLocationBitmapRenderer", e)
        }
    }

    private fun registerLocationReceiver() {
        val filter = IntentFilter("eu.tijlb.LOCATION_DB_UPDATE")
        context?.registerReceiver(locationReceiver, filter, RECEIVER_NOT_EXPORTED)
        Log.d(TAG, "Registered location receiver in LastLocationBitmapRenderer")
    }
}