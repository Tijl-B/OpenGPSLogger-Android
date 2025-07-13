package eu.tijlb.opengpslogger.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.ui.view.LayeredMapView

private const val TAG = "ogl-browsefragment"

class BrowseFragment : Fragment(R.layout.fragment_browse) {

    private lateinit var layeredMapView: LayeredMapView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "Creating browse fragment view")
        super.onViewCreated(view, savedInstanceState)

        layeredMapView = view.findViewById(R.id.layeredMapView)
    }

    override fun onPause() {
        Log.d(TAG, "Browse fragment onPause")
        layeredMapView.cancelJobs()
        super.onPause()
    }
}