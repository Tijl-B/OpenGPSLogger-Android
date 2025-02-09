package eu.tijlb.opengpslogger.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())
    private val matrix = Matrix()

    private var scaleFactor = 1f
    private var minScale = 0.4f
    private var maxScale = 50f
    private var lastFocusX = 0f
    private var lastFocusY = 0f

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        centerImage()
    }

    private fun centerImage() {
        drawable
            ?.let {
                val imageWidth = it.intrinsicWidth
                val imageHeight = it.intrinsicHeight
                val viewWidth = width
                val viewHeight = height

                val translateX = (viewWidth - imageWidth) / 2f
                val translateY = (viewHeight - imageHeight) / 2f

                matrix.postTranslate(translateX, translateY)
                invalidate()
            }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if (event.pointerCount > 1) {
            lastFocusX = event.getX(0)
            lastFocusY = event.getY(0)
        }

        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.concat(matrix)
        super.onDraw(canvas)
        canvas.restore()
    }

    private inner class ScaleListener : ScaleGestureDetector.OnScaleGestureListener {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scale = detector.scaleFactor
            val newScale = scaleFactor * scale
            if (newScale in minScale..maxScale) {
                scaleFactor = newScale
                matrix.postScale(scale, scale, detector.focusX, detector.focusY)
                invalidate()
            }
            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true

        override fun onScaleEnd(detector: ScaleGestureDetector) {}
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            matrix.postTranslate(-distanceX, -distanceY)
            invalidate()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            matrix.reset()
            scaleFactor = 1f
            invalidate()
            return true
        }
    }
}