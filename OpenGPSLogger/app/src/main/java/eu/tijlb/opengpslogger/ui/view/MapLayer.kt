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

    fun startDrawJob(bbox: BBoxDto, renderDimension: Pair<Int, Int>, invalidate: () -> Unit) {
        job = CoroutineScope(Dispatchers.IO).launch {
            drawLayerOverride(bbox, renderDimension, invalidate)
            invalidate
        }
    }

    fun cancelJob() {
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
                Log.d(
                    "ogl-maplayer",
                    "Drawing bitmap with translation x ${offsetX} y ${offsetY} and scale $scaleAmount"
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
        Log.d("ogl-maplayer", "Committing pan by $amountToPanX, $amountToPanY")

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

        Log.d("ogl-maplayer", "Panning bitmap with offset x $amountToPanX and y $amountToPanY")
        val matrix = Matrix().apply {
            setTranslate(deltaX, deltaY)
        }

        canvas.drawBitmap(bitmap, matrix, null)
        return pannedBitMap
    }

    private fun calculateScaleAmount(visualZoom: Double): Float {
        val scaleBetweenLevels = 2.0.pow((visualZoom - zoom))
        Log.d(
            "ogl-maplayer",
            "scaleBetweenLevels $scaleBetweenLevels = 2^($visualZoom - $zoom)"
        )
        return scaleBetweenLevels.toFloat()
    }

    private suspend fun drawLayerWhenReady(
        bbox: BBoxDto,
        renderDimension: Pair<Int, Int>,
        invalidate: () -> Unit
    ) {
        bitmapRenderer.draw(
            bbox,
            zoom,
            renderDimension,
            { },
            { }
        )?.let {
            bitmap = it
            invalidate()
        }
    }

    private suspend fun drawLayerOverride(
        bbox: BBoxDto,
        renderDimension: Pair<Int, Int>,
        invalidate: () -> Unit
    ) {
        var tmpBitmap: Bitmap? = null
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

        Log.d("ogl-maplayer", "Zooming bitmap from $oldZoom to $newZoom")

        val zoomedBitmap = createBitmap(bitMap.width, bitMap.height)
        val canvas = Canvas(zoomedBitmap)

        val scale = 2.0F.pow((newZoom - oldZoom))
        Log.d(
            "ogl-maplayer",
            "Need to scale by $scale to go from zoom $oldZoom to $newZoom"
        )

        val matrix = Matrix().apply {
            setScale(scale, scale, bitMap.width / 2f, bitMap.height / 2f)
        }

        canvas.drawBitmap(bitMap, matrix, null)
        return zoomedBitmap
    }

}