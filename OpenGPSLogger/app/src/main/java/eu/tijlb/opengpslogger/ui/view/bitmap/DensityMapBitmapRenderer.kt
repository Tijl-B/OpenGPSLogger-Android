package eu.tijlb.opengpslogger.ui.view.bitmap

import android.content.Context
import android.graphics.Bitmap
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
import kotlin.math.tan

class DensityMapBitmapRenderer(val context: Context) {

    private val densityMapAdapter: DensityMapAdapter = DensityMapAdapter.getInstance(context)

    var onPointProgressUpdateListener: OnPointProgressUpdateListener? = null

    suspend fun draw(
        bbox: BBoxDto,
        zoomLevel: Int,
        renderDimension: Pair<Int, Int>,
        assignBitmap: (Bitmap) -> Unit,
        refreshView: () -> Any
    ): Bitmap? {
        Log.d("ogl-imagerendererview", "Drawing density map...")
        if (!coroutineContext.isActive) {
            Log.d("ogl-imagerendererview", "Stop drawing density map!")
            return null
        }

        val subdivisions = densityMapAdapter.getSubdivisions(zoomLevel)
        val sparseDensityMap = SparseDensityMap(subdivisions, subdivisions)
        var adaptedClusterBitmap = createBitmap(renderDimension.first, renderDimension.second)

        assignBitmap(adaptedClusterBitmap)

        var i = 0
        densityMapAdapter.getPoints(bbox, zoomLevel)
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

                            val xIndex = cursor.getLong(xIndexColumnIndex)
                            val yIndex = cursor.getLong(yIndexColumnIndex)
                            val time = cursor.getLong(timeColumnIndex)
                            val amount = cursor.getLong(countColumnIndex)

                            if (xIndex >= 0 && xIndex <= sparseDensityMap.width && yIndex >= 0 && yIndex <= sparseDensityMap.height) {
                                sparseDensityMap.put(xIndex, yIndex, amount)
                            }

                            if ((++i) % 10000 == 0) {
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

        adaptedClusterBitmap = extractAndScaleBitmap(sparseDensityMap, adaptedClusterBitmap, bbox)
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

        val pixels = IntArray(dstWidth * dstHeight)
        for (i in pixels.indices) {
            val x = i % dstWidth
            val y = i / dstWidth

            val originalX = (x.toDouble() / dstWidth) * srcWidth + left - 0.5
            val originalY = (y.toDouble() / dstHeight) * srcHeight + top - 0.5

            val originalXL = originalX.toLong()
            val originalYL = originalY.toLong()

            val fx = originalX - originalXL
            val fy = originalY - originalYL

            val q11 = sourceBitMap.get(originalXL, originalYL)
            val q21 = sourceBitMap.get(originalXL + 1, originalYL)
            val q12 = sourceBitMap.get(originalXL, originalYL + 1)
            val q22 = sourceBitMap.get(originalXL + 1, originalYL + 1)

            val amount = (1 - fx) * (1 - fy) * q11 +
                    fx * (1 - fy) * q21 +
                    (1 - fx) * fy * q12 +
                    fx * fy * q22

            pixels[i] = if (amount > 0) ColorUtil.toDensityColor(amount.toLong(), 10_000L) else 0
        }
        targetBitmap.setPixels(pixels, 0, dstWidth, 0, 0, dstWidth, dstHeight)


        var result = targetBitmap
        return result
    }

    fun blurBitmapMultiplePasses(
        context: Context,
        bitmap: Bitmap,
        radius: Float,
        passes: Int
    ): Bitmap {
        var blurred = bitmap
        Log.d("ogl-imagerendererview", "Blurring bitmap $passes times with radius $radius")
        repeat(passes) {
            blurred = blurBitmap(context, blurred, radius)
        }
        return blurred
    }

    fun blurBitmap(context: Context, bitmap: Bitmap, radius: Float): Bitmap {
        if (radius < 0.1F) {
            return bitmap
        }
        Log.d("ogl-imagerendererview", "Blurring bitmap with a radius of $radius")
        val renderScript = RenderScript.create(context)
        val input = Allocation.createFromBitmap(renderScript, bitmap)
        val output = Allocation.createTyped(renderScript, input.type)
        val script = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
        script.setRadius(radius.coerceIn(0.1f, 25f))
        script.setInput(input)
        script.forEach(output)
        val blurred = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        output.copyTo(blurred)
        renderScript.destroy()
        return blurred
    }
}