package eu.tijlb.opengpslogger.ui.view.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.core.graphics.createBitmap
import eu.tijlb.opengpslogger.model.database.location.LocationDbContract
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.database.settings.ColorMode
import eu.tijlb.opengpslogger.model.dto.VisualisationSettingsDto
import eu.tijlb.opengpslogger.model.dto.query.PointsQuery
import eu.tijlb.opengpslogger.model.util.ColorUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

private const val TAG = "ogl-pointsbitmaprenderer"

class PointsBitmapRenderer(
    val context: Context,
    var visualisationSettings: VisualisationSettingsDto
) {

    var onPointProgressUpdateListener: OnPointProgressUpdateListener? = null

    private val locationDbHelper: LocationDbHelper =
        LocationDbHelper.getInstance(context.applicationContext)

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED }

    private val millisPerHour = 60 * 60 * 1000
    private val millisPerDay = 24 * millisPerHour
    private val millisPerMonth = (30.436875 * millisPerDay).roundToLong()
    private val millisPerYear = (365.2425 * millisPerDay).roundToLong()
    private val maxTimeDeltaMillis: Long
        get() = TimeUnit.MINUTES.toMillis(visualisationSettings.connectLinesMaxMinutesDelta)

    private var pointRadius = 5F
        set(value) {
            field = value
            dotPaint.apply {
                val shadowRadius = (value + 1F) * 1.1F
                val shadowOffset = value * 0.5F
                val shadowOpacityPercentage = visualisationSettings.shadowOpacity
                if (shadowOpacityPercentage == 0) {
                    clearShadowLayer()
                } else {
                    val color = Color.valueOf(0F, 0F, 0F, (shadowOpacityPercentage / 100F))
                    setShadowLayer(shadowRadius, shadowOffset, shadowOffset, color.pack())
                }
            }
        }

    suspend fun draw(
        query: PointsQuery,
        latConverter: (Double) -> Double,
        lonConverter: (Double) -> Double,
        renderDimension: Pair<Int, Int>,
        assignBitmap: (Bitmap) -> Unit,
        refreshView: () -> Any
    ) {
        Log.d(
            TAG,
            "Drawing coordinates with query: $query, renderDimension: $renderDimension"
        )
        if (!coroutineContext.isActive) {
            Log.d(TAG, "Stop drawing points!")
            return
        }

        if (renderDimension.first <= 0 || renderDimension.second <= 0) {
            Log.e(TAG, "Invalid render dimension, cannot render points bitmap: $renderDimension.")
            return
        }
        val clusterBitmap = createBitmap(renderDimension.first, renderDimension.second)
        val canvas = Canvas(clusterBitmap)
        assignBitmap(clusterBitmap)

        var currentTimeBucket: Long = 0
        var prevLat: Double? = null
        var prevLon: Double? = null
        var prevTime = 0L
        var i = 0

        withContext(Dispatchers.IO) {
            locationDbHelper.getPointsCursor(query).use { c ->
                if (!coroutineContext.isActive) {
                    Log.d(TAG, "Stop drawing points!")
                    return@withContext
                }
                if (!c.moveToFirst()) return@withContext

                Log.d(TAG, "Start iterating over points cursor")
                val amountOfPointsToLoad = c.count
                Log.d(TAG, "Count done $amountOfPointsToLoad")
                onPointProgressUpdateListener?.onPointProgressMax(
                    amountOfPointsToLoad
                )

                pointRadius = visualisationSettings.dotSize ?: when {
                    amountOfPointsToLoad < 10_000 -> 10F
                    amountOfPointsToLoad < 100_000 -> 5F
                    else -> 2F
                }

                linePaint.strokeWidth = visualisationSettings.lineSize ?: (pointRadius * 2)

                val latIndex = c.getColumnIndex(LocationDbContract.COLUMN_NAME_LATITUDE)
                val lonIndex = c.getColumnIndex(LocationDbContract.COLUMN_NAME_LONGITUDE)
                val timeIndex = c.getColumnIndex(LocationDbContract.COLUMN_NAME_TIMESTAMP)

                do {
                    if (!coroutineContext.isActive) return@withContext
                    val latitude = c.getDouble(latIndex)
                    val longitude = c.getDouble(lonIndex)
                    val time = c.getLong(timeIndex)

                    currentTimeBucket = drawPoint(
                        latitude,
                        longitude,
                        prevLat,
                        prevLon,
                        time,
                        prevTime,
                        currentTimeBucket,
                        canvas,
                        latConverter,
                        lonConverter
                    )

                    prevLat = latitude
                    prevLon = longitude
                    prevTime = time
                    if (++i % 10_000 == 0) {
                        Log.d(TAG, "refreshing points bitmap $i")
                        onPointProgressUpdateListener?.onPointProgressUpdate(i)
                        refreshView()
                    }
                } while (c.moveToNext())
            }
        }
        Log.d(TAG, "Done drawing points...")
        onPointProgressUpdateListener?.onPointProgressUpdate(i)
        refreshView()
    }

    private fun drawPoint(
        latitude: Double,
        longitude: Double,
        prevLatitude: Double?,
        prevLongitude: Double?,
        time: Long,
        prevTime: Long,
        currentTimeBucket: Long,
        canvas: Canvas,
        latConverter: (Double) -> Double,
        lonConverter: (Double) -> Double
    ): Long {
        val newTimeBucket = when (visualisationSettings.colorMode) {
            ColorMode.SINGLE_COLOR -> 1
            ColorMode.MULTI_COLOR_YEAR -> time / millisPerYear
            ColorMode.MULTI_COLOR_MONTH -> time / millisPerMonth
            ColorMode.MULTI_COLOR_DAY -> time / millisPerDay
            ColorMode.MULTI_COLOR_HOUR -> time / millisPerHour
        }
        var timeBucket = currentTimeBucket
        if (newTimeBucket != currentTimeBucket) {
            timeBucket = newTimeBucket
            val opacity = (visualisationSettings.opacityPercentage * 255 / 100.0).roundToInt()
            val newColor =
                ColorUtil.generateColor(newTimeBucket + visualisationSettings.colorSeed, opacity)
            dotPaint.color = newColor
            linePaint.color = newColor
            Log.d(
                TAG,
                "Changed color to $newColor in time bucket $newTimeBucket (${visualisationSettings.colorMode}) (timestamp $time)"
            )

        }

        val x = lonConverter(longitude).toFloat()
        val y = latConverter(latitude).toFloat()

        if (x in 0f..canvas.width.toFloat() && y in 0f..canvas.height.toFloat()) {
            canvas.drawCircle(x, y, pointRadius, dotPaint)
        }

        if (visualisationSettings.drawLines
            && prevLongitude != null && prevLatitude != null
            && abs(time - prevTime) < maxTimeDeltaMillis
        ) {
            val prevX = lonConverter(prevLongitude).toFloat()
            val prevY = latConverter(prevLatitude).toFloat()
            if (max(abs(x - prevX) / canvas.height, abs(y - prevY) / canvas.width) < 0.25) {
                canvas.drawLine(prevX, prevY, x, y, linePaint)
            }
        }

        return timeBucket
    }

    interface OnPointProgressUpdateListener {
        fun onPointProgressMax(max: Int)
        fun onPointProgressUpdate(progress: Int)
    }
}
