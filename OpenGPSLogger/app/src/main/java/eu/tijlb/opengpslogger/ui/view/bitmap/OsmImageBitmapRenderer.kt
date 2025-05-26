package eu.tijlb.opengpslogger.ui.view.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.model.util.OsmGeometryUtil
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class OsmImageBitmapRenderer(val context: Context) {

    var onTileProgressUpdateListener: OnTileProgressUpdateListener? = null

    suspend fun draw(
        bbox: BBoxDto,
        zoom: Int,
        smurl: String,
        assignBitmap: (Bitmap) -> Unit,
        refreshView: () -> Any
    ) {
        val minLat = bbox.minLat
        val maxLat = bbox.maxLat
        val minLon = bbox.minLon
        val maxLon = bbox.maxLon

        val (xmin, ymax) = OsmGeometryUtil.deg2num(minLat, minLon, zoom)
        val (xmax, ymin) = OsmGeometryUtil.deg2num(maxLat, maxLon, zoom)

        Log.d(
            "ogl-osmhelper",
            "Requesting tiles from ($xmin, $ymin) to ($xmax, $ymax) with zoom $zoom for bbox $bbox"
        )

        val imgSizePx = 256

        val xRange = xmax.toInt() - xmin.toInt() + 1
        val yRange = ymax.toInt() - ymin.toInt() + 1

        val realXRange = xmax - xmin
        val realYRange = ymax - ymin

        val width = ceil(realXRange * imgSizePx).toInt()
        val height = ceil(realYRange * imgSizePx).toInt()

        if (width == 0 || height == 0) {
            Log.e(
                "ogl-osmhelper",
                "Cannot get image cluster, width $width ($xmax - $xmin) and / or height $height ($ymax - $ymin) is 0"
            )
            return
        }

        val clusterBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        assignBitmap(clusterBitmap)
        val canvas = Canvas(clusterBitmap)

        val paint = Paint()

        onTileProgressUpdateListener?.onTileProgressMax(xRange * yRange)
        // Fetch and draw tiles within the requested area
        var i = 0
        if (!coroutineContext.isActive) {
            Log.d("ogl-osmhelper", "Interrupting...")
            return
        }
        for (xtile in xmin.toInt()..xmax.toInt()) {
            for (ytile in ymin.toInt()..ymax.toInt()) {
                try {
                    val imgUrl = smurl.replace("{z}", zoom.toString())
                        .replace("{x}", xtile.toString())
                        .replace("{y}", ytile.toString())
                    Log.d("ogl-osmhelper", "Requesting osm url $imgUrl")
                    getOrDownloadImage(imgUrl) { tileBitMap ->
                        val visibleMinX = max(xmin, xtile.toDouble())
                        val visibleMinY = max(ymin, ytile.toDouble())
                        val visibleMaxX = min(xmax, xtile + 1.0)
                        val visibleMaxY = min(ymax, ytile + 1.0)

                        if (visibleMinX < visibleMaxX && visibleMinY < visibleMaxY) {

                            val srcLeft = ((visibleMinX - xtile) * imgSizePx).toInt()
                            val srcTop = ((visibleMinY - ytile) * imgSizePx).toInt()
                            val srcRight = ((visibleMaxX - xtile) * imgSizePx).toInt()
                            val srcBottom = ((visibleMaxY - ytile) * imgSizePx).toInt()

                            Log.d(
                                "ogl-osmhelper",
                                "srcRect: left=$srcLeft, top=$srcTop, right=$srcRight, bottom=$srcBottom"
                            )
                            val srcRect = Rect(srcLeft, srcTop, srcRight, srcBottom)

                            val destLeft = ((visibleMinX - xmin) / realXRange * width).toInt()
                            val destTop = ((visibleMinY - ymin) / realYRange * height).toInt()
                            val destRight = ((visibleMaxX - xmin) / realXRange * width).toInt()
                            val destBottom = ((visibleMaxY - ymin) / realYRange * height).toInt()

                            Log.d(
                                "ogl-osmhelper",
                                "destRect: left=$destLeft, top=$destTop, right=$destRight, bottom=$destBottom"
                            )
                            val destRect = Rect(destLeft, destTop, destRight, destBottom)

                            canvas.drawBitmap(tileBitMap, srcRect, destRect, paint)
                        } else {
                            Log.w(
                                "ogl-osmhelper",
                                "Tile ($xtile, $ytile) does not intersect with the requested area"
                                        + "xtile $xtile xmin $xmin ytile $ytile "
                            )
                        }
                        refreshView()
                    }

                } catch (e: Exception) {
                    Log.e("ogl-osmhelper", "Couldn't download image: ${e.message}", e)

                }
                if (!coroutineContext.isActive) {
                    Log.d("ogl-osmhelper", "Interrupting...")
                    return
                }
                onTileProgressUpdateListener?.onTileProgressUpdate(++i)
            }
        }
        Log.d("ogl-osmhelper", "Done loading osm background")
    }

    fun getOrDownloadImage(url: String, callback: (Bitmap) -> Unit) {
        Glide.with(context)
            .asBitmap()
            .load(url)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    callback(resource)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    Log.d("ogl-osmhelper", "Load cleared for url $url")
                }
            })

    }

    interface OnTileProgressUpdateListener {
        fun onTileProgressMax(max: Int)

        fun onTileProgressUpdate(progress: Int)
    }
}
