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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

private const val TAG = "ogl-maplayer"

class MapLayer(val bitmapRenderer: AbstractBitmapRenderer) {

    private var bitmap: Bitmap? = null
    private var job: Job? = null

    var zoom = 4.0
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

    suspend fun startDrawJob(
        bbox: BBoxDto,
        zoom: Double,
        renderDimension: Pair<Int, Int>,
        invalidate: () -> Unit
    ): Job {
        cancelAndJoin()
        Log.d(TAG, "Starting draw job coroutine")
        val coroutine = CoroutineScope(Dispatchers.IO).launch {
            try {
                drawLayerOverride(bbox, zoom, renderDimension, invalidate)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error while drawing bitmap", e)
            }
        }
        job = coroutine
        return coroutine
    }

    suspend fun cancelAndJoin() {
        Log.d(TAG, "Canceling job $job")
        job?.cancelAndJoin()
    }

    fun cancelJob() {
        Log.d(TAG, "Canceling job $job")
        job?.cancel()
    }

    fun requiresUpdate(visualZoom: Double): Boolean {
        return zoom.toInt() != visualZoom.toInt()
    }

    fun drawBitmapOnCanvas(canvas: Canvas, visualZoom: Double) {
        try{
            bitmap?.let {
                if (!bitmapRenderer.redrawOnTranslation()) {
                    canvas.drawBitmap(it, 0f, 0f, null)
                    return
                }
                canvas.drawBitmap(it, matrix, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unknown exception", e)
        }
    }

    fun commitPanAndZoom(): Matrix? {
        if (!bitmapRenderer.redrawOnTranslation()) {
            return null
        }

        val matrixToApply = Matrix(matrix)
        if (!matrixToApply.isIdentity) {
            bitmap?.let {
                val newBitmap = createBitmap(it.width, it.height)
                val canvas = Canvas(newBitmap)
                canvas.drawBitmap(it, matrixToApply, null)
                bitmap = newBitmap
                it.recycle()
            }
        }

        applyInverseMatrix(matrixToApply)
        return matrixToApply
    }

    private fun applyInverseMatrix(matrixToApply: Matrix) {
        val appliedMatrixInverse = Matrix()
        if (matrixToApply.invert(appliedMatrixInverse)) {
            matrix.postConcat(appliedMatrixInverse)
        }
    }

    private suspend fun drawLayerOverride(
        bbox: BBoxDto,
        zoom: Double,
        renderDimension: Pair<Int, Int>,
        postInvalidate: () -> Unit
    ) {
        this.zoom = zoom
        var tmpBitmap: Bitmap? = null
        Log.d(TAG, "Calling bitmapRenderer.draw")
        bitmapRenderer.draw(
            bbox, zoom,
            renderDimension,
            { bmp ->
                tmpBitmap = bmp
            },
            {
                try {
                    Log.d(TAG, "RefreshView called with $bitmap")
                    bitmap?.let {
                        val canvas = Canvas(it)
                        tmpBitmap?.let { bm ->
                            canvas.drawBitmap(bm, 0F, 0F, null)
                            postInvalidate()
                        }
                    } ?: run {
                        bitmap = tmpBitmap
                        postInvalidate()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected exception", e)
                }
            }
        )?.let {
            bitmap = it
            postInvalidate()
        }
    }
}