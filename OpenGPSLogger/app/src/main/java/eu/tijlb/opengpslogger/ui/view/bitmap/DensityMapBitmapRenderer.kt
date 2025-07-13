package eu.tijlb.opengpslogger.ui.view.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import androidx.core.graphics.createBitmap
import eu.tijlb.opengpslogger.model.bitmap.SparseDensityMap
import eu.tijlb.opengpslogger.model.database.densitymap.DensityMapAdapter
import eu.tijlb.opengpslogger.model.database.densitymap.continent.DensityMapDbContract
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.model.util.ColorUtil
import eu.tijlb.opengpslogger.ui.view.bitmap.PointsBitmapRenderer.OnPointProgressUpdateListener
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

class DensityMapBitmapRenderer(val context: Context) : AbstractBitmapRenderer() {

    private val densityMapAdapter: DensityMapAdapter = DensityMapAdapter.getInstance(context)

    var onPointProgressUpdateListener: OnPointProgressUpdateListener? = null

    override suspend fun draw(
        bbox: BBoxDto,
        zoom: Int,
        renderDimension: Pair<Int, Int>,
        assignBitmap: (Bitmap) -> Unit,
        refreshView: () -> Any
    ): Bitmap? {
        Log.d("ogl-imagerendererview", "Drawing density map...")
        if (!coroutineContext.isActive) {
            Log.d("ogl-imagerendererview", "Stop drawing density map!")
            return null
        }
        if (renderDimension.first == 0 || renderDimension.second == 0) {
            Log.d("ogl-imagerendererview", "renderDimension: $renderDimension")
            return null
        }

        val subdivisions = densityMapAdapter.getSubdivisions(zoom)
        val sparseDensityMap = SparseDensityMap(subdivisions, subdivisions)
        var adaptedClusterBitmap = createBitmap(renderDimension.first, renderDimension.second)

        assignBitmap(adaptedClusterBitmap)

        var i = 0
        densityMapAdapter.getPoints(bbox, zoom)
            .use { cursor ->
                run {
                    if (!coroutineContext.isActive) {
                        Log.d("ogl-imagerendererview-point", "Stop drawing density map!")
                        return null
                    }
                    Log.d("ogl-imagerendererview-point", "Start iterating over points cursor")
                    if (cursor.moveToFirst()) {
                        Log.d("ogl-imagerendererview-point", "Starting count")
                        val amountOfPointsToLoad = cursor.count
                        Log.d("ogl-imagerendererview-point", "Count done $amountOfPointsToLoad")
                        onPointProgressUpdateListener?.onPointProgressMax(
                            amountOfPointsToLoad
                        )

                        val xIndexColumnIndex =
                            cursor.getColumnIndex(DensityMapDbContract.COLUMN_NAME_X_INDEX)
                        val yIndexColumnIndex =
                            cursor.getColumnIndex(DensityMapDbContract.COLUMN_NAME_Y_INDEX)
                        val timeColumnIndex =
                            cursor.getColumnIndex(DensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME)
                        val countColumnIndex =
                            cursor.getColumnIndex(DensityMapDbContract.COLUMN_NAME_AMOUNT)

                        Log.d("ogl-imagerendererview-point", "Got first point from cursor")
                        do {
                            if (!coroutineContext.isActive) {
                                Log.d("ogl-imagerendererview-point", "Stop drawing density map!")
                                return null
                            }

                            val xIndex = cursor.getFloat(xIndexColumnIndex)
                            val yIndex = cursor.getFloat(yIndexColumnIndex)
                            val time = cursor.getLong(timeColumnIndex)
                            val amount = cursor.getLong(countColumnIndex)

                            val color = ColorUtil.toDensityColor(amount, 10_000L)
                            if (xIndex >= 0 && xIndex <= sparseDensityMap.width && yIndex >= 0 && yIndex <= sparseDensityMap.height) {
                                sparseDensityMap.put(xIndex, yIndex, color)
                            }

                            if ((++i) % 100000 == 0) {
                                Log.d(
                                    "ogl-imagerendererview-point",
                                    "refreshing density map bitmap $i"
                                )
                                adaptedClusterBitmap = extractAndScaleBitmap(
                                    sparseDensityMap,
                                    adaptedClusterBitmap,
                                    bbox
                                )
                                assignBitmap(adaptedClusterBitmap)
                                onPointProgressUpdateListener?.onPointProgressUpdate(i)
                                refreshView()
                            }

                        } while (cursor.moveToNext())
                    }
                }
            }

        adaptedClusterBitmap =
            extractAndScaleBitmap(sparseDensityMap, adaptedClusterBitmap, bbox, true)
        assignBitmap(adaptedClusterBitmap)
        onPointProgressUpdateListener?.onPointProgressUpdate(i)
        Log.d("ogl-imagerendererview-point", "Done drawing density map...")
        refreshView()
        return adaptedClusterBitmap
    }

    private fun extractAndScaleBitmap(
        sourceBitMap: SparseDensityMap,
        targetBitmap: Bitmap,
        bbox: BBoxDto,
        blur: Boolean = false
    ): Bitmap {
        Log.d(
            "ogl-imagerendererview",
            "Extracting and scaling sparse bitmap into full bitmap with bbox $bbox"
        )
        val worldWidth = sourceBitMap.width.toDouble()
        val worldHeight = sourceBitMap.height.toDouble()

        fun lonToX(lon: Double): Int = ((lon + 180) / 360 * worldWidth).toInt()
        fun latToY(lat: Double): Int {
            val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
            val latRad = Math.toRadians(clampedLat)
            val mercatorY = (1.0 - ln(tan(Math.PI / 4 + latRad / 2)) / Math.PI) / 2.0
            return (mercatorY * worldHeight).toInt()
        }

        val left = lonToX(bbox.minLon).coerceIn(0, sourceBitMap.width - 1)
        val right = lonToX(bbox.maxLon).coerceIn(0, sourceBitMap.width - 1)
        val top = latToY(bbox.maxLat).coerceIn(0, sourceBitMap.height - 1)
        val bottom = latToY(bbox.minLat).coerceIn(0, sourceBitMap.height - 1)

        val srcWidth = maxOf(1, right - left)
        val srcHeight = maxOf(1, bottom - top)

        val dstWidth = targetBitmap.width
        val dstHeight = targetBitmap.height

        val cellWidth = dstWidth.toFloat() / srcWidth
        val cellHeight = dstHeight.toFloat() / srcHeight

        val canvas = Canvas(targetBitmap)
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

        val paint = Paint()

        for ((pos, color) in sourceBitMap.data) {
            val (x, y) = pos
            if (x in left.toFloat()..right.toFloat() && y in top.toFloat()..bottom.toFloat()) {
                val mappedX = ((x - left).toDouble() / srcWidth * dstWidth).toFloat()
                val mappedY = ((y - top).toDouble() / srcHeight * dstHeight).toFloat()

                paint.color = color

                val maxHeightWidth = max(cellWidth, cellHeight)
                canvas.drawCircle(mappedX, mappedY, max(2F, maxHeightWidth * 0.71F), paint)
            }
        }
        return targetBitmap
    }
}