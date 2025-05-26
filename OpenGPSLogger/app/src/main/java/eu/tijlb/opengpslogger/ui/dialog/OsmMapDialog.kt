package eu.tijlb.opengpslogger.ui.dialog

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import eu.tijlb.opengpslogger.R

class OsmMapDialog(private val onDismiss: () -> Unit) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_osm_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //val osmMapView: OsmMapView = view.findViewById(R.id.osmMapView)
        val closeButton: View = view.findViewById(R.id.closeButton)

        closeButton.setOnClickListener {
            Log.d("ogl-osmmapdialog", "User pressed close button")
            onDismiss()
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onDestroyView() {
        Log.d("ogl-osmmapdialog", "View was destroyed")
        super.onDestroyView()
        onDismiss()
    }
}