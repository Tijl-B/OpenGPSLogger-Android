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
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}