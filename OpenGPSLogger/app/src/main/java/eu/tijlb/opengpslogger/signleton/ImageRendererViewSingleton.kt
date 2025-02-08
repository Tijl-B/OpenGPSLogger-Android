package eu.tijlb.opengpslogger.signleton

import android.os.Handler
import android.os.Looper
import android.util.Log
import eu.tijlb.opengpslogger.view.ImageRendererView
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

    fun resetPoints() {
        Handler(Looper.getMainLooper()).post {
            viewRef?.get()?.resetIfDrawn()
        }
    }
}
