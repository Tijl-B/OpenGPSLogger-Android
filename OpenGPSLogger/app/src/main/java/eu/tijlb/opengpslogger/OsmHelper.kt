package eu.tijlb.opengpslogger

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import eu.tijlb.opengpslogger.dto.BBoxDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

class OsmHelper {

    var onTileProgressUpdateListener: OnTileProgressUpdateListener? = null

    suspend fun getImageCluster(
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

        val (xmin, ymax) = deg2num(minLat, minLon, zoom)
        val (xmax, ymin) = deg2num(maxLat, maxLon, zoom)

        Log.d(
            "ogl-osmhelper",
            "Requesting tiles from ($xmin, $ymin) to ($xmax, $ymax) with zoom $zoom for bbox $bbox"
        )

        val imgSizePx = 256

        val xRange = xmax.toInt() - xmin.toInt() + 1
        val yRange = ymax.toInt() - ymin.toInt() + 1

        val realXRange = xmax - xmin
        val realYRange = ymax - ymin

        val width = (realXRange * imgSizePx).toInt()
        val height = (realYRange * imgSizePx).toInt()

        val clusterBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        assignBitmap(clusterBitmap)
        val canvas = Canvas(clusterBitmap)

        canvas.drawColor(Color.GRAY)

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
                    val imgUrl = smurl.format(zoom, xtile, ytile)
                    Log.d("ogl-osmhelper", "Requesting osm url $imgUrl")
                    val tileBitmap = downloadImage(imgUrl)

                    tileBitmap?.let {
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

                            canvas.drawBitmap(tileBitmap, srcRect, destRect, paint)
                        } else {
                            Log.w(
                                "ogl-osmhelper",
                                "Tile ($xtile, $ytile) does not intersect with the requested area"
                                        + "xtile $xtile xmin $xmin ytile $ytile "
                            )
                        }
                    }
                    refreshView()
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

    // Helper functions to convert between coordinates and tiles
    fun lat2num(lat: Double, zoom: Int): Double {
        val n = 2.0.pow(zoom)
        val latRad = Math.toRadians(lat)
        return n * (1.0 - (ln(tan(latRad) + 1 / cos(latRad)) / Math.PI)) / 2.0
    }

    fun lon2num(lon: Double, zoom: Int): Double {
        val n = 2.0.pow(zoom)
        return n * (lon + 180.0) / 360.0
    }

    fun deg2num(lat: Double, lon: Double, zoom: Int): Pair<Double, Double> {
        val xtile = lon2num(lon, zoom)
        val ytile = lat2num(lat, zoom)
        return Pair(xtile, ytile)
    }

    suspend fun downloadImage(urlString: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val connection = url.openConnection()
                connection.connect()
                val inputStream: InputStream = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                bitmap
            } catch (e: Exception) {
                Log.e("ogl-osmhelper", "Error downloading image: $e")
                null
            }
        }
    }

    interface OnTileProgressUpdateListener {
        fun onTileProgressMax(max: Int)

        fun onTileProgressUpdate(progress: Int)
    }
}
