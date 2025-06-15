package eu.tijlb.opengpslogger.ui.fragment;

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.ui.view.OsmMapView

class BrowseFragment : Fragment(R.layout.fragment_browse) {

    private lateinit var osmMapView: OsmMapView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("ogl-brosefragment", "Creating brose fragment view")
        super.onViewCreated(view, savedInstanceState)

        osmMapView = view.findViewById(R.id.osmMapView)

        // Initialize your map if needed
        // osmMapView.init() or similar if your view requires it
    }

    override fun onResume() {
        super.onResume()
        // osmMapView.onResume() if your custom map requires it
    }

    override fun onPause() {
        super.onPause()
        // osmMapView.onPause() if needed
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // osmMapView.onDestroy() if needed
    }
}