package eu.tijlb.opengpslogger.ui.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.Log
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

class MapLayer(val bitmapRenderer: AbstractBitmapRenderer) {

    private var bitmap: Bitmap? = null
    private var job: Job? = null

    private var zoom = 4
    private var offsetX = 0f
    private var offsetY = 0f

    fun visualPan(dx: Float, dy: Float) {
        offsetX -= dx
        offsetY -= dy
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
            if(!bitmapRenderer.redrawOnTranslation()) {
                canvas.drawBitmap(it, 0f, 0f, null)
                return
            }
            canvas.withTranslation(offsetX, offsetY) {
                val scaleAmount = calculateScaleAmount(visualZoom)
                scale(
                    scaleAmount,
                    scaleAmount,
                    width / 2F,
                    height / 2F
                )
                drawBitmap(it, 0f, 0f, null)
            }
        }
    }

    fun commitZoom(newZoom: Int) {
        if(!bitmapRenderer.redrawOnTranslation()) {
            return
        }
        val oldZoom = zoom
        bitmap = bitmap?.let {
            zoomBitmap(it, oldZoom, newZoom)
        }
        zoom = newZoom
    }

    fun commitPan(visualZoom: Double): Triple<Float, Float, Int>? {
        if(!bitmapRenderer.redrawOnTranslation()) {
            return null
        }

        val amountToPanX = -offsetX
        val amountToPanY = -offsetY
        if (amountToPanX == 0F && amountToPanY == 0F) {
            return null
        }
        val newBitmap = bitmap?.let {
            panBitmap(it, amountToPanX, amountToPanY, visualZoom)
        }
        bitmap = newBitmap
        offsetX += amountToPanX
        offsetY += amountToPanY
        Log.d(TAG, "Committing pan by $amountToPanX, $amountToPanY")

        val deltaX = amountToPanX / calculateScaleAmount(visualZoom)
        val deltaY = amountToPanY / calculateScaleAmount(visualZoom)
        return Triple(deltaX, deltaY, zoom)
    }

    private fun panBitmap(
        bitmap: Bitmap,
        amountToPanX: Float,
        amountToPanY: Float,
        visualZoom: Double
    ): Bitmap {
        val pannedBitMap = createBitmap(bitmap.width, bitmap.height)
        val canvas = Canvas(pannedBitMap)

        val deltaX = -amountToPanX / calculateScaleAmount(visualZoom)
        val deltaY = -amountToPanY / calculateScaleAmount(visualZoom)

        Log.d(TAG, "Panning bitmap with offset x $amountToPanX and y $amountToPanY")
        val matrix = Matrix().apply {
            setTranslate(deltaX, deltaY)
        }

        canvas.drawBitmap(bitmap, matrix, null)
        return pannedBitMap
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
                    }
                } ?: run { bitmap = tmpBitmap }
                invalidate()
            }
        )?.let {
            bitmap = it
            invalidate()
        }
    }

    private fun zoomBitmap(bitMap: Bitmap, oldZoom: Int, newZoom: Int): Bitmap {
        if (oldZoom == newZoom)
            return bitMap

        Log.d(TAG, "Zooming bitmap from $oldZoom to $newZoom")

        val zoomedBitmap = createBitmap(bitMap.width, bitMap.height)
        val canvas = Canvas(zoomedBitmap)

        val scale = 2.0F.pow((newZoom - oldZoom))
        Log.d(
            TAG,
            "Need to scale by $scale to go from zoom $oldZoom to $newZoom"
        )

        val matrix = Matrix().apply {
            setScale(scale, scale, bitMap.width / 2f, bitMap.height / 2f)
        }

        canvas.drawBitmap(bitMap, matrix, null)
        return zoomedBitmap
    }

}