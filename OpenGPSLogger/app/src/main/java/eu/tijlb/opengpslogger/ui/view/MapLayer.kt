package eu.tijlb.opengpslogger.ui.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.Log
import android.view.ScaleGestureDetector
import androidx.core.graphics.createBitmap
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.ui.view.bitmap.AbstractBitmapRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "ogl-maplayer"

class MapLayer(val bitmapRenderer: AbstractBitmapRenderer, val width: Int, val height: Int) {

    private var bitmap: Bitmap? = null
    private var job: Job? = null

    var zoom = 4

    private val matrix = Matrix()

    fun onScroll(dx: Float, dy: Float) {
        matrix.postTranslate(-dx, -dy)
    }

    fun onScale(detector: ScaleGestureDetector) {
        matrix.postScale(
            detector.scaleFactor,
            detector.scaleFactor,
            detector.focusX,
            detector.focusY
        )

    }

    fun startDrawJob(bbox: BBoxDto, zoom: Int, renderDimension: Pair<Int, Int>, invalidate: () -> Unit): Job {
        cancelJob()
        Log.d(TAG, "Starting draw job coroutine")
        val coroutine = CoroutineScope(Dispatchers.IO).launch {
            drawLayerOverride(bbox, zoom, renderDimension, invalidate)
        }
        job = coroutine
        return coroutine
    }

    fun cancelJob() {
        Log.d(TAG, "Canceling job $job")
        job?.cancel()
    }

    fun requiresUpdate(visualZoom: Double): Boolean {
        return zoom != visualZoom.toInt()
    }

    fun drawBitmapOnCanvas(canvas: Canvas, visualZoom: Double) {
        bitmap?.let {
            if (!bitmapRenderer.redrawOnTranslation()) {
                canvas.drawBitmap(it, 0f, 0f, null)
                return
            }
            canvas.drawBitmap(it, matrix, null)
        }
    }

    fun commitPanAndZoom(): Matrix? {
        if (!bitmapRenderer.redrawOnTranslation()) {
            return null
        }

        val matrix = Matrix(matrix)
        if(!matrix.isIdentity) {
            bitmap?.let {
                val newBitmap = createBitmap(it.width, it.height)
                val canvas = Canvas(newBitmap)
                canvas.drawBitmap(it, matrix, null)
                bitmap = newBitmap
                it.recycle()
            }
        }
        this.matrix.reset()
        return matrix
    }

    private suspend fun drawLayerOverride(
        bbox: BBoxDto,
        zoom: Int,
        renderDimension: Pair<Int, Int>,
        invalidate: () -> Unit
    ) {
        this.zoom = zoom
        var tmpBitmap: Bitmap? = null
        Log.d(TAG, "Calling bitmapRenderer.draw")
        bitmapRenderer.draw(
            bbox, zoom,
            renderDimension,
            { bmp -> tmpBitmap = bmp },
            {
                bitmap?.let {
                    val canvas = Canvas(it)
                    tmpBitmap?.let { bm ->
                        canvas.drawBitmap(bm, 0F, 0F, null)
                        invalidate
                    }
                } ?: run {
                    bitmap = tmpBitmap
                    invalidate
                }
                invalidate()
            }
        )?.let {
            bitmap = it
            invalidate()
        }
    }
}