package eu.tijlb.opengpslogger.ui.view.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.core.graphics.createBitmap
import eu.tijlb.opengpslogger.model.database.lastlocation.LastLocationHelper
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.model.util.OsmGeometryUtil.lat2num
import eu.tijlb.opengpslogger.model.util.OsmGeometryUtil.lon2num
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

private const val TAG = "ogl-lastlocationbitmaprenderer"

class LastLocationBitmapRenderer(val context: Context) : AbstractBitmapRenderer() {

    private var lastLocationHelper = LastLocationHelper(context)

    override suspend fun draw(
        bbox: BBoxDto,
        zoom: Int,
        renderDimension: Pair<Int, Int>,
        assignBitmap: (Bitmap) -> Unit,
        refreshView: () -> Any
    ): Bitmap? {
        if (!coroutineContext.isActive) {
            Log.d(TAG, "Stop drawing last known location!")
            return null
        }
        if (renderDimension.first == 0 || renderDimension.second == 0) {
            Log.d(TAG, "renderDimension: $renderDimension")
            return null
        }

        val bitmap = createBitmap(renderDimension.first, renderDimension.second)
        val canvas = Canvas(bitmap)

        lastLocationHelper.getLastLocation()?.let {
            val osmX = lon2num(it.longitude, zoom)
            val osmY = lat2num(it.latitude, zoom)

            val minX = lon2num(bbox.minLon, zoom)
            val maxX = lon2num(bbox.maxLon, zoom)
            val minY = lat2num(bbox.minLat, zoom)
            val maxY = lat2num(bbox.maxLat, zoom)

            val xRange = (maxX - minX)
            val yRange = (maxY - minY)

            val mappedX = ((osmX - minX) / xRange * bitmap.width).roundToInt()
            val mappedY = ((maxY - osmY) / yRange * bitmap.height).roundToInt()

            val paint = Paint().apply {
                isAntiAlias = true
                color = Color.WHITE
            }

            canvas.drawCircle(mappedX.toFloat(), mappedY.toFloat(), 20F, paint)
            val pointAge = System.currentTimeMillis() - it.time
            paint.color =
                if (pointAge < 5.minutes.inWholeMilliseconds)
                    Color.BLUE
                else Color.GRAY

            canvas.drawCircle(mappedX.toFloat(), mappedY.toFloat(), 14F, paint)
            Log.d(TAG, "Drawing last known location to x $mappedX, y $mappedY...")
        } ?: run { Log.d(TAG, "Not drawing last known location as it is null") }

        assignBitmap(bitmap)
        refreshView()
        return bitmap
    }
}