package eu.tijlb.opengpslogger.ui.dialog

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.ui.view.ZoomableImageView

class ZoomableImageDialog(private val bitmap: Bitmap, private val onDismiss: () -> Unit) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_zoomable_image, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val zoomableImageView: ZoomableImageView = view.findViewById(R.id.zoomableImageView)
        val closeButton: View = view.findViewById(R.id.closeButton)

        bitmap.let {
            zoomableImageView.setImageBitmap(it)
        }

        closeButton.setOnClickListener {
            Log.d("ogl-zoomableimagedialog", "User pressed close button")
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
        Log.d("ogl-zoomableimagedialog", "View was destroyed")
        super.onDestroyView()
        onDismiss()
    }
}