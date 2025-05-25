package eu.tijlb.opengpslogger.ui.singleton

import android.os.Handler
import android.os.Looper
import android.util.Log
import eu.tijlb.opengpslogger.ui.view.ImageRendererView
import java.lang.ref.WeakReference

object ImageRendererViewSingleton {
    private var viewRef: WeakReference<ImageRendererView>? = null

    fun registerView(view: ImageRendererView) {
        viewRef?.get()
            ?.let {
                Log.w(
                    "ogl-imagerendererviewsingleton",
                    "Setting image renderer view, but one was already set."
                )
            }
        viewRef = WeakReference(view)
    }

    fun redrawPoints() {
        Handler(Looper.getMainLooper()).post {
            viewRef?.get()?.redrawCoordinateData = true
        }
    }

    fun redrawOsm() {
        Handler(Looper.getMainLooper()).post {
            viewRef?.get()?.redrawOsm = true
        }
    }
}
