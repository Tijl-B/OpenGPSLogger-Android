package eu.tijlb.opengpslogger.ui.view.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.graphics.createBitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import eu.tijlb.opengpslogger.model.database.tileserver.TileServerDbHelper
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.model.util.OsmGeometryUtil
import eu.tijlb.opengpslogger.ui.util.LockUtil.lockWithTimeout
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.text.clear
import kotlin.text.get
import kotlin.text.replace
import kotlin.time.Duration.Companion.seconds

private const val TAG = "ogl-osmimagebitmaprenderer"

class OsmImageBitmapRenderer(val context: Context) : AbstractBitmapRenderer() {

    private var tileServerDbHelper: TileServerDbHelper = TileServerDbHelper.getInstance(context)

    var onTileProgressUpdateListener: OnTileProgressUpdateListener? = null

    var currentJob: Job? = null

    private var drawMutex = Mutex()
    private val latestDrawJob = AtomicReference<Job?>(null)

    override suspend fun draw(
        bbox: BBoxDto,
        zoom: Double,
        renderDimension: Pair<Int, Int>,
        assignBitmap: (Bitmap) -> Unit,
        refreshView: () -> Any
    ): Bitmap? {
        Log.d(TAG, "Starting osm draw")
        val currentJob = Job()
        latestDrawJob.getAndSet(currentJob)?.cancel()
        if (!currentJob.isActive) {
            Log.d(TAG, "CurrentJob is not active, not drawing osm bitmap")
            return null
        }

        drawMutex.lockWithTimeout(30.seconds) {
            if (latestDrawJob.get() != currentJob || !currentJob.isActive) {
                Log.d(TAG, "Got lock but job is not active, not drawing osm bitmap")
                return@lockWithTimeout null
            }
            Log.d(TAG, "Start drawing osm bitmap")
            draww(bbox, zoom.toInt(), renderDimension, assignBitmap, refreshView)
            currentJob.complete()
        }
        return null
    }

    private suspend fun draww(
        bbox: BBoxDto,
        zoom: Int,
        renderDimension: Pair<Int, Int>,
        assignBitmap: (Bitmap) -> Unit,
        refreshView: () -> Any
    ) {
        currentJob?.cancel()

        val smurl = tileServerDbHelper.getSelectedUrl()

        val minLat = bbox.minLat
        val maxLat = bbox.maxLat
        val minLon = bbox.minLon
        val maxLon = bbox.maxLon

        val (xmin, ymax) = OsmGeometryUtil.deg2num(minLat, minLon, zoom)
        val (xmax, ymin) = OsmGeometryUtil.deg2num(maxLat, maxLon, zoom)

        Log.d(
            TAG,
            "Requesting tiles from ($xmin, $ymin) to ($xmax, $ymax) with zoom $zoom for bbox $bbox"
        )

        val imgSizePx = 256

        val xRange = xmax.toInt() - xmin.toInt() + 1
        val yRange = ymax.toInt() - ymin.toInt() + 1

        val realXRange = xmax - xmin
        val realYRange = ymax - ymin

        val width = renderDimension.first
        val height = renderDimension.second

        val clusterBitmap = createBitmap(width, height)
        assignBitmap(clusterBitmap)
        val canvas = Canvas(clusterBitmap)

        val paint = Paint()

        onTileProgressUpdateListener?.onTileProgressMax(xRange * yRange)
        // Fetch and draw tiles within the requested area
        var i = 0
        if (!coroutineContext.isActive) {
            Log.d(TAG, "Interrupting...")
            return
        }
        val job = Job()
        currentJob = job
        for (xtile in xmin.toInt()..xmax.toInt()) {
            for (ytile in ymin.toInt()..ymax.toInt()) {
                try {
                    val imgUrl = smurl.replace("{z}", zoom.toString())
                        .replace("{x}", xtile.toString())
                        .replace("{y}", ytile.toString())
                    Log.d(TAG, "Requesting osm url $imgUrl")
                    val cancellationLambda = getOrDownloadImage(imgUrl) { tileBitMap ->
                        if (!job.isActive) {
                            Log.d(TAG, "Interrupting request to $imgUrl...")
                            return@getOrDownloadImage
                        }

                        val visibleMinX = max(xmin, xtile.toDouble())
                        val visibleMinY = max(ymin, ytile.toDouble())
                        val visibleMaxX = min(xmax, xtile + 1.0)
                        val visibleMaxY = min(ymax, ytile + 1.0)

                        if (visibleMinX < visibleMaxX && visibleMinY < visibleMaxY) {

                            val srcLeft = ((visibleMinX - xtile) * imgSizePx).toInt()
                            val srcTop = ((visibleMinY - ytile) * imgSizePx).toInt()
                            val srcRight = ((visibleMaxX - xtile) * imgSizePx).toInt()
                            val srcBottom = ((visibleMaxY - ytile) * imgSizePx).toInt()

                            val srcRect = Rect(srcLeft, srcTop, srcRight, srcBottom)

                            val destLeft = ((visibleMinX - xmin) / realXRange * width).toInt()
                            val destTop = ((visibleMinY - ymin) / realYRange * height).toInt()
                            val destRight = ((visibleMaxX - xmin) / realXRange * width).toInt()
                            val destBottom = ((visibleMaxY - ymin) / realYRange * height).toInt()

                            val destRect = Rect(destLeft, destTop, destRight, destBottom)

                            canvas.drawBitmap(tileBitMap, srcRect, destRect, paint)
                            Log.d(TAG, "Drew tile $imgUrl")
                            refreshView()
                        } else {
                            Log.w(
                                TAG,
                                "Tile ($xtile, $ytile) does not intersect with the requested area"
                                        + "xtile $xtile xmin $xmin ytile $ytile "
                            )
                        }
                        onTileProgressUpdateListener?.onTileProgressUpdate(++i)
                    }
                    job.invokeOnCompletion { cancellationLambda() }

                } catch (e: Exception) {
                    Log.e(TAG, "Couldn't download image: ${e.message}", e)

                }
                if (!coroutineContext.isActive) {
                    Log.i(TAG, "Interrupting...")
                    return
                }
            }
        }
        Log.d(TAG, "Done loading osm background")
        return
    }

    private fun getOrDownloadImage(url: String, callback: (Bitmap) -> Unit): () -> Unit {
        val target = Glide.with(context)
            .asBitmap()
            .load(url)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    Log.d(TAG, "Resource $url ready")
                    callback(resource)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    Log.d(TAG, "Load cleared for url $url")
                }
            })

        return { Glide.with(context).clear(target) }
    }

    interface OnTileProgressUpdateListener {
        fun onTileProgressMax(max: Int)

        fun onTileProgressUpdate(progress: Int)
    }
}
