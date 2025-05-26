package eu.tijlb.opengpslogger.ui.view.bitmap

import android.annotation.SuppressLint
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
import kotlinx.coroutines.isActive
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class PointsBitmapRenderer(
    val context: Context,
    var visualisationSettings: VisualisationSettingsDto
) {

    var onPointProgressUpdateListener: OnPointProgressUpdateListener? = null

    private val locationDbHelper: LocationDbHelper = LocationDbHelper.getInstance(context)

    private val dotPaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
    }

    private val linePaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
    }

    private val millisPerHour = 60 * 60 * 1000
    private val millisPerDay = 24 * millisPerHour
    private val millisPerMonth = (30.436875 * millisPerDay).roundToLong()
    private val millisPerYear = (365.2425 * millisPerDay).roundToLong()
    private val maxTimeDeltaMillis: Long
        get() = TimeUnit.MINUTES.toMillis(visualisationSettings.connectLinesMaxMinutesDelta)

    private var pointRadius = 5F

    @SuppressLint("Range")
    suspend fun drawCoordinates(
        query: PointsQuery,
        latConverter: (Double) -> Double,
        lonConverter: (Double) -> Double,
        renderDimension: Pair<Int, Int>,
        assignBitmap: (Bitmap) -> Unit,
        refreshView: () -> Any
    ) {
        Log.d("ogl-pointsbitmaprenderer", "Drawing coordinates...")
        if (!coroutineContext.isActive) {
            Log.d("ogl-pointsbitmaprenderer", "Stop drawing points!")
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

        locationDbHelper.getPointsCursor(query)
            .use { cursor ->
                run {
                    if (!coroutineContext.isActive) {
                        Log.d("ogl-pointsbitmaprenderer", "Stop drawing points!")
                        return
                    }
                    Log.d("ogl-pointsbitmaprenderer", "Start iterating over points cursor")
                    if (cursor.moveToFirst()) {
                        Log.d("00000", "Starting count")
                        val amountOfPointsToLoad = cursor.count
                        Log.d("00000", "Count done $amountOfPointsToLoad")
                        onPointProgressUpdateListener?.onPointProgressMax(
                            amountOfPointsToLoad
                        )
                        pointRadius = visualisationSettings.dotSize ?: when {
                            amountOfPointsToLoad < 10000 -> 10F
                            amountOfPointsToLoad < 100000 -> 5F
                            else -> 2F
                        }

                        linePaint.strokeWidth = visualisationSettings.lineSize ?: (pointRadius * 2)

                        val latColumnIndex =
                            cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_LATITUDE)
                        val longColumnIndex =
                            cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_LONGITUDE)
                        val timeColumnIndex =
                            cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_TIMESTAMP)
                        Log.d("ogl-pointsbitmaprenderer", "Got first point from cursor")
                        do {
                            if (!coroutineContext.isActive) {
                                Log.d("ogl-pointsbitmaprenderer", "Stop drawing points!")
                                return@run
                            }

                            val longitude = cursor.getDouble(longColumnIndex)
                            val latitude = cursor.getDouble(latColumnIndex)
                            val time = cursor.getLong(timeColumnIndex)


                            currentTimeBucket = draw(
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
                            if ((++i) % 10000 == 0) {
                                Log.d("ogl-pointsbitmaprenderer", "refreshing points bitmap $i")
                                onPointProgressUpdateListener?.onPointProgressUpdate(i)
                                refreshView()
                            }

                        } while (cursor.moveToNext())
                    }
                }
            }
        onPointProgressUpdateListener?.onPointProgressUpdate(i)
        Log.d("ogl-pointsbitmaprenderer", "Done drawing points...")
        refreshView()
    }


    private fun draw(
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
            val opacity = (visualisationSettings.opacityPercentage * (255.0 / 100.0)).roundToInt()
            val newColor =
                ColorUtil.generateColor(newTimeBucket + visualisationSettings.colorSeed, opacity)
            dotPaint.color = newColor
            linePaint.color = newColor
            Log.d(
                "ogl-pointsbitmaprenderer",
                "Changed color to $newColor in time bucket $newTimeBucket (${visualisationSettings.colorMode}) (timestamp $time)"
            )

        }

        val x = lonConverter(longitude).toFloat()
        val y = latConverter(latitude).toFloat()

        if (x >= 0 && x <= canvas.width && y >= 0 && y <= canvas.height) {
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