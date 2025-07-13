package eu.tijlb.opengpslogger.ui.view.bitmap

import android.content.Context
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.location.Location
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.lifecycle.lifecycleScope
import eu.tijlb.opengpslogger.model.bitmap.SparseDensityMap
import eu.tijlb.opengpslogger.model.broadcast.LocationUpdateReceiver
import eu.tijlb.opengpslogger.model.database.densitymap.continent.DensityMapDbContract
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.model.util.ColorUtil
import eu.tijlb.opengpslogger.model.util.OsmGeometryUtil.lat2num
import eu.tijlb.opengpslogger.model.util.OsmGeometryUtil.lon2num
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.tan

private const val TAG = "ogl-lastlocationbitmaprenderer"

class LastLocationBitmapRenderer(val context: Context) : AbstractBitmapRenderer() {

    private var locationReceiver: LocationUpdateReceiver

   var lastLocation: Location? = null

    init {
        locationReceiver = LocationUpdateReceiver().apply {
            setOnLocationReceivedListener {
                Log.d(TAG, "Updating last known location to $it")
                lastLocation = it
            }
        }
        registerLocationReceiver()
    }

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

        lastLocation?.let {
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
            paint.color = Color.BLUE
            canvas.drawCircle(mappedX.toFloat(), mappedY.toFloat(), 14F, paint)
            Log.d(TAG, "Drawing last known location to x $mappedX, y $mappedY...")
        }?:run { Log.d(TAG, "Not drawing last known location as it is null") }

        assignBitmap(bitmap)
        refreshView()
        return bitmap
    }

    override fun onStop() {
        unregisterLocationReceiver()
    }

    override fun onResume() {
        registerLocationReceiver()
    }

    private fun unregisterLocationReceiver() {
        try {
            context.unregisterReceiver(locationReceiver)
            Log.d(TAG, "Unregistered location receiver in LastLocationBitmapRenderer")
        } catch (e: IllegalArgumentException) {
            Log.i(TAG, "Failed to unregistered location receiver in LastLocationBitmapRenderer", e)
        }
    }

    private fun registerLocationReceiver() {
        val filter = IntentFilter("eu.tijlb.LOCATION_UPDATE")
        context.registerReceiver(locationReceiver, filter, RECEIVER_NOT_EXPORTED)
        Log.d(TAG, "Registered location receiver in LastLocationBitmapRenderer")
    }

}