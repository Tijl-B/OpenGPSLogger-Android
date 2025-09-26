package eu.tijlb.opengpslogger.ui.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.Log
import android.view.ScaleGestureDetector
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.ui.view.bitmap.AbstractBitmapRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.pow

private const val TAG = "ogl-maplayer"

class MapLayer(val bitmapRenderer: AbstractBitmapRenderer, val width: Int, val height: Int) {

    private var bitmap: Bitmap? = null
    private var job: Job? = null

    private var zoom = 4
    private var offsetX = 0f
    private var offsetY = 0f

    private val matrix = Matrix()

    fun onScroll(dx: Float, dy: Float) {
        offsetX -= dx
        offsetY -= dy
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

    fun startDrawJob(bbox: BBoxDto, renderDimension: Pair<Int, Int>, invalidate: () -> Unit): Job {
        cancelJob()
        Log.d(TAG, "Starting draw job coroutine")
        val coroutine = CoroutineScope(Dispatchers.IO).launch {
            drawLayerOverride(bbox, renderDimension, invalidate)
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
            //canvas.drawBitmap(it, matrix, null)
            oldDrawBitmapOnCanvas(canvas, visualZoom, it)
        }
    }

    private fun oldDrawBitmapOnCanvas(
        canvas: Canvas,
        visualZoom: Double,
        bitmap: Bitmap
    ) {
        canvas.withTranslation(offsetX, offsetY) {
            val scaleAmount = calculateScaleAmount(visualZoom)
            scale(
                scaleAmount,
                scaleAmount,
                width / 2F,
                height / 2F
            )
            drawBitmap(bitmap, 0f, 0f, null)
        }
    }


    private fun commitZoom(visualZoom: Double, matrix: Matrix): Int {
        val newZoom = visualZoom.toInt()
        val oldZoom = zoom
        if (oldZoom == newZoom)
            return oldZoom
        Log.d(TAG, "Committing zoom from $oldZoom to $newZoom")

        bitmap?.let {
            val scale = 2.0F.pow((newZoom - oldZoom))
            Log.d(
                TAG,
                "Need to scale by $scale to go from zoom $oldZoom to $newZoom"
            )
            matrix.postScale(scale, scale, it.width / 2f, it.height / 2f)
        }
        return newZoom
    }

    fun commitPanAndZoom(visualZoom: Double): Triple<Float, Float, Int>? {
        if (!bitmapRenderer.redrawOnTranslation()) {
            return null
        }

        val matrix = Matrix()
        val amountToPanX = -offsetX
        val amountToPanY = -offsetY
        val v = commitPan(amountToPanX, amountToPanY, visualZoom, matrix)
        val newZoom = commitZoom(visualZoom, matrix)
        bitmap?.let {
            val newBitmap = createBitmap(it.width, it.height)
            val canvas = Canvas(newBitmap)
            canvas.drawBitmap(it, matrix, null)
            bitmap = newBitmap
        }
        zoom = newZoom
        offsetX += amountToPanX
        offsetY += amountToPanY
        return v
    }

    private fun commitPan(
        amountToPanX: Float,
        amountToPanY: Float,
        visualZoom: Double, matrix: Matrix
    ): Triple<Float, Float, Int>? {
        Log.d(TAG, "Committing pan with x $amountToPanX y $amountToPanY")

        val deltaX = amountToPanX / calculateScaleAmount(visualZoom)
        val deltaY = amountToPanY / calculateScaleAmount(visualZoom)
        Log.d(TAG, "Panning bitmap with offset x $deltaX and y $deltaY")
        matrix.postTranslate(-deltaX, -deltaY)

        Log.d(TAG, "Committing pan by $amountToPanX, $amountToPanY")

        this.matrix.postTranslate(deltaX, deltaY)
        return Triple(deltaX, deltaY, zoom)
    }

    private fun calculateScaleAmount(visualZoom: Double): Float {
        val scaleBetweenLevels = 2.0.pow((visualZoom - zoom))
        return scaleBetweenLevels.toFloat()
    }

    private suspend fun drawLayerOverride(
        bbox: BBoxDto,
        renderDimension: Pair<Int, Int>,
        invalidate: () -> Unit
    ) {
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