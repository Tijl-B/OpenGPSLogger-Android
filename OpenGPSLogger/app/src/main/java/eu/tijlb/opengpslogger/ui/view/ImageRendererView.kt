package eu.tijlb.opengpslogger.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import eu.tijlb.opengpslogger.model.database.location.LocationDbContract
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.database.settings.ColorMode
import eu.tijlb.opengpslogger.model.database.settings.VisualisationSettingsHelper
import eu.tijlb.opengpslogger.model.database.tileserver.TileServerDbHelper
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.model.dto.VisualisationSettingsDto
import eu.tijlb.opengpslogger.model.dto.query.PointsQuery
import eu.tijlb.opengpslogger.model.util.ColorUtil
import eu.tijlb.opengpslogger.model.util.OsmGeometryUtil
import eu.tijlb.opengpslogger.ui.view.bitmap.OsmImageBitmapRenderer
import eu.tijlb.opengpslogger.ui.singleton.ImageRendererViewSingleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class ImageRendererView(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {


    private var visualisationSettingsHelper: VisualisationSettingsHelper
    private var visualisationSettingsChangedListener: OnSharedPreferenceChangeListener
    private var tileServerDbHelper: TileServerDbHelper

    var onTilesLoadedListener: OnTilesLoadedListener? = null
    var onPointsLoadedListener: OnPointsLoadedListener? = null
    var onTileProgressUpdateListener: OsmImageBitmapRenderer.OnTileProgressUpdateListener? = null
        set(value) {
            field = value
            osmImageBitmapRenderer.onTileProgressUpdateListener = onTileProgressUpdateListener
        }
    var onPointProgressUpdateListener: OnPointProgressUpdateListener? = null

    var aspectRatio = 1.0
        set(value) {
            if (field != value) {
                Log.d("ogl-imagerendererview", "Aspect ratio changed from $field to $value")
                field = value
                pointsRenderHeight = (pointsRenderWidth / aspectRatio).toInt()
                CoroutineScope(Dispatchers.Main)
                    .launch {
                        layoutParams = ViewGroup.LayoutParams(width, (width / value).toInt())
                    }
            }
        }
    var pointsRenderWidth = width
        set(value) {
            if (value > 0) {
                field = value
                pointsRenderHeight = (pointsRenderWidth / aspectRatio).toInt()
            }
        }

    var dataSource = "All"
    var inputBbox: BBoxDto? = null

    var beginTime: LocalDate? = null
    var endTime: LocalDate? = null

    var minAccuracy: Float? = null
    var minAngle = 0F

    var redrawOsm = true
        set(value) {
            field = value
            if (value) {
                invalidate()
            }
        }
    var redrawPoints = true
        set(value) {
            field = value
            if (value) {
                invalidate()
            }
        }

    private var pointsRenderHeight = height

    private val osmImageBitmapRenderer: OsmImageBitmapRenderer = OsmImageBitmapRenderer(context)

    private val locationDbHelper: LocationDbHelper = LocationDbHelper.getInstance(getContext())
    private val millisPerHour = 60 * 60 * 1000
    private val millisPerDay = 24 * millisPerHour
    private val millisPerMonth = (30.436875 * millisPerDay).roundToLong()
    private val millisPerYear = (365.2425 * millisPerDay).roundToLong()
    private var osmBitMap: Bitmap? = null
    private var osmJob: Job? = null
    private var osmLock = Mutex()
    private var pointsBitMap: Bitmap? = null
    private var pointsCoroutine: Job? = null
    private var pointsLock = Mutex()
    private var copyrightBitMap: Bitmap? = null
    private var zoom = 10
    private var xMin = 0.0
    private var yMin = 0.0
    private var xRange = 1.0
    private var yRange = 1.0
    private var pointRadius = 5F
    private var visualisationSettings: VisualisationSettingsDto
    private var maxTimeDeltaMillis: Long

    init {
        tileServerDbHelper = TileServerDbHelper(context)
        visualisationSettingsHelper = VisualisationSettingsHelper(context)
        visualisationSettingsChangedListener =
            visualisationSettingsHelper.registerVisualisationSettingsChangedListener {
                Log.d(
                    "ogl-imagerendererview",
                    "Executing callback on visualisation settings changed."
                )
                visualisationSettings = it
                maxTimeDeltaMillis =
                    TimeUnit.MINUTES.toMillis(visualisationSettings.connectLinesMaxMinutesDelta)
                redrawPoints = true
            }
        visualisationSettings = visualisationSettingsHelper.getVisualisationSettings()
        maxTimeDeltaMillis =
            TimeUnit.MINUTES.toMillis(visualisationSettings.connectLinesMaxMinutesDelta)
        ImageRendererViewSingleton.registerView(this)
    }


    fun redrawPointsAndOsm() {
        redrawOsm = true
        redrawPoints = true
    }

    private suspend fun cancelOsmCoroutine() {
        osmLock.withLock {
            osmJob?.takeIf { it.isActive }?.cancelAndJoin()
            osmJob = null
            osmBitMap = null
        }
    }

    private suspend fun cancelPointCoroutine() {
        pointsLock.withLock {
            Log.d("ogl-imagerendererview", "Resetting point drawing...")
            pointsCoroutine?.takeIf { it.isActive }?.cancelAndJoin()
            pointsCoroutine = null
            pointsBitMap = null
        }
    }

    private val dotPaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
    }
    private val linePaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
    }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    val copyrightBarPaint = Paint().apply {
        color = Color.BLACK
        alpha = 100
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (layoutParams == null) {
            Log.d("ogl-imagerendererview", "Not drawing since layoutParams is null")
            return
        }
        drawMap(canvas)
        Log.d("ogl-imagerendererview", "Finished onDraw")
    }

    override fun onDetachedFromWindow() {
        Log.d("ogl-imagerendererview", "Detaching from window...")
        super.onDetachedFromWindow()
        visualisationSettingsHelper.deregisterAdvancedFiltersChangedListener(
            visualisationSettingsChangedListener
        )
        CoroutineScope(Dispatchers.IO)
            .launch {
                cancelOsmCoroutine()
                cancelPointCoroutine()
            }
    }

    private fun drawMap(canvas: Canvas) {
        Log.d("ogl-imagerendererview", "Rendering source $dataSource from $beginTime till $endTime")

        if (beginTime == null || endTime == null) {
            Log.d("ogl-imagerendererview", "No begin or end time, not drawing...")
            return
        }

        Log.d("ogl-imagerendererview", "redrawOsm $redrawOsm, redrawPoints $redrawPoints")

        if (redrawPoints || redrawOsm) {
            val shouldRedrawPoints = redrawPoints
            val shouldRedrawOsm = redrawOsm
            redrawPoints = false
            redrawOsm = false
            CoroutineScope(Dispatchers.IO)
                .launch {
                    val realBbox = inputBbox
                        ?: locationDbHelper.getCoordsRange(pointsQuery())
                            .expand(0.05)
                    calculateXYValues(realBbox)

                    if (shouldRedrawPoints) {
                        Log.d("ogl-imagerendererview", "Redrawing points...")
                        cancelPointCoroutine()
                        launchPointsCoroutine(realBbox)
                    }
                    if (shouldRedrawOsm) {
                        Log.d("ogl-imagerendererview", "Redrawing osm...")
                        cancelOsmCoroutine()
                        launchOsmCoroutine(realBbox)
                    }
                }
        }

        Log.d(
            "ogl-imagerendererview",
            "Drawing osmBitmap and pointsBitmap to canvas with w ${canvas.width} h ${canvas.height}"
        )

        drawBitmap(canvas, osmBitMap)
        drawBitmap(canvas, pointsBitMap)
        drawBitmap(canvas, copyrightBitMap)
    }

    private fun drawBitmap(canvas: Canvas, bitMap: Bitmap?) {
        bitMap?.let {
            Bitmap.createScaledBitmap(it, canvas.width, canvas.height, true)
        }?.also {
            canvas.drawBitmap(it, 0f, 0f, null)
        }
    }

    private fun launchPointsCoroutine(realBbox: BBoxDto) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d("ogl-imagerendererview", "Getting pointLock")
            pointsLock.withLock {
                if (!isActive) {
                    Log.d("ogl-imagerendererview", "Stop loading points!")
                    return@withLock
                }
                if (pointsBitMap == null && pointsCoroutine?.isActive != true) {
                    Log.d("ogl-imagerendererview", "Loading points")
                    updateVisualisationSettings()
                    val pointsQuery = pointsQuery(realBbox)

                    Log.d("ogl-imagerendererview", "Calculating amount of points...")
                    if (!isActive) {
                        Log.d("ogl-imagerendererview", "Stop calculating points!")
                        return@withLock
                    }

                    Log.d("ogl-imagerendererview", "Starting coroutine for drawing points...")
                    pointsCoroutine = createPointsCoroutine(pointsQuery)
                }
            }
        }
    }

    private fun launchOsmCoroutine(realBbox: BBoxDto) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d("ogl-imagerendererview", "Getting osmLock")
            osmLock.withLock {
                if (osmBitMap == null && osmJob?.isActive != true) {
                    Log.d("ogl-imagerendererview", "Loading osm")
                    loadOsmBackgroundAsync(realBbox)
                }
            }
        }
    }

    private fun createPointsCoroutine(
        pointsQuery: PointsQuery
    ) = CoroutineScope(Dispatchers.IO).launch {
        val latConverter = { lat: Double ->
            (OsmGeometryUtil.lat2num(lat, zoom) - yMin) / yRange * pointsRenderHeight
        }
        val lonConverter = { lon: Double ->
            (OsmGeometryUtil.lon2num(lon, zoom) - xMin) / xRange * pointsRenderWidth
        }
        drawCoordinates(
            pointsQuery,
            latConverter,
            lonConverter
        )
    }

    private fun pointsQuery(bbox: BBoxDto? = null) = PointsQuery(
        startDateMillis = getStartDateMillis(),
        endDateMillis = getEndDateMillis(),
        dataSource = dataSource,
        bbox = bbox,
        minAccuracy = minAccuracy,
        minAngle = minAngle
    )

    private fun loadOsmBackgroundAsync(bbox: BBoxDto) {
        osmJob = CoroutineScope(Dispatchers.IO).launch {
            val clusterBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            osmBitMap = clusterBitmap
            osmImageBitmapRenderer.getImageCluster(
                bbox,
                zoom,
                getBasemapUrl(),
                { bitmap -> osmBitMap = bitmap }
            )
            { invalidate() }
            onTilesLoadedListener?.onTilesLoaded()
        }
    }

    private fun getBasemapUrl(): String {
        updateCopyrightNotice()
        return tileServerDbHelper.getSelectedUrl()
    }

    private fun updateCopyrightNotice() {
        val copyrightNotice = tileServerDbHelper.getSelectedCopyrightNotice()
        setCopyrightNotice(copyrightNotice)
    }

    private fun setCopyrightNotice(copyrightNotice: String) {
        if (copyrightNotice.isEmpty()) copyrightBitMap = null

        if (width == 0 || height == 0) return

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        copyrightBitMap = bitmap

        val textBounds = Rect()
        textPaint.getTextBounds(copyrightNotice, 0, copyrightNotice.length, textBounds)
        val textWidth = textBounds.width().toFloat()
        val textHeight = textBounds.height().toFloat()

        val barHeight = textHeight * 1.2F
        val barWidth = textWidth + 16

        val barLeft = width - barWidth
        val barTop = height - barHeight
        canvas.drawRect(barLeft, barTop, width.toFloat(), height.toFloat(), copyrightBarPaint)

        val textX = barLeft + 8
        val textY = height - textHeight * 0.2F
        canvas.drawText(copyrightNotice, textX, textY, textPaint)
    }

    private fun calculateXYValues(bbox: BBoxDto) {
        zoom = OsmGeometryUtil.calculateZoomLevel(bbox)
        val (xmin, ymax) = OsmGeometryUtil.deg2num(bbox.minLat, bbox.minLon, zoom)
        val (xmax, ymin) = OsmGeometryUtil.deg2num(bbox.maxLat, bbox.maxLon, zoom)
        xMin = xmin
        yMin = ymin
        xRange = xmax - xmin
        yRange = ymax - ymin
        aspectRatio = xRange / yRange
    }

    @SuppressLint("Range")
    private suspend fun drawCoordinates(
        query: PointsQuery,
        latConverter: (Double) -> Double,
        lonConverter: (Double) -> Double
    ) {
        Log.d("ogl-imagerendererview", "Drawing coordinates...")
        if (!coroutineContext.isActive) {
            Log.d("ogl-imagerendererview", "Stop drawing points!")
            invalidate()
            return
        }

        val clusterBitmap =
            Bitmap.createBitmap(pointsRenderWidth, pointsRenderHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(clusterBitmap)
        pointsBitMap = clusterBitmap
        var currentTimeBucket: Long = 0

        var prevLat: Double? = null
        var prevLon: Double? = null
        var prevTime = 0L

        var i = 0

        locationDbHelper.getPointsCursor(query)
            .use { cursor ->
                run {
                    if (!coroutineContext.isActive) {
                        Log.d("ogl-imagerendererview-point", "Stop drawing points!")
                        invalidate()
                        return
                    }
                    Log.d("ogl-imagerendererview-point", "Start iterating over points cursor")
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
                        Log.d("ogl-imagerendererview-point", "Got first point from cursor")
                        do {
                            if (!coroutineContext.isActive) {
                                Log.d("ogl-imagerendererview-point", "Stop drawing points!")
                                invalidate()
                                cursor.close()
                                return
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
                                Log.d("ogl-imagerendererview-point", "refreshing points bitmap $i")
                                onPointProgressUpdateListener?.onPointProgressUpdate(i)
                                invalidate()
                            }

                        } while (cursor.moveToNext())
                    }
                }
            }
        onPointProgressUpdateListener?.onPointProgressUpdate(i)
        Log.d("ogl-imagerendererview-point", "Done drawing points...")
        invalidate()
        onPointsLoadedListener?.onPointsLoaded()
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
            val opacity = (visualisationSettings.opacityPercentage * ( 255.0 / 100.0)).roundToInt()
            val newColor = ColorUtil.generateColor(newTimeBucket + visualisationSettings.colorSeed, opacity)
            dotPaint.color = newColor
            linePaint.color = newColor
            Log.d(
                "ogl-imagerendererview-point-color",
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
            if (max(abs(x - prevX) / height, abs(y - prevY) / width) < 0.25) {
                canvas.drawLine(prevX, prevY, x, y, linePaint)
            }
        }

        return timeBucket
    }

    private fun getStartDateMillis() = (beginTime?.atStartOfDay(ZoneId.systemDefault())
        ?.toInstant()
        ?.toEpochMilli()
        ?: 0L)

    private fun getEndDateMillis(): Long {
        val endDateMillis = endTime?.atTime(23, 59, 59)
            ?.atZone(ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli()
            ?: Long.MAX_VALUE
        return endDateMillis
    }

    private fun updateVisualisationSettings() {
        visualisationSettings = visualisationSettingsHelper.getVisualisationSettings()
        maxTimeDeltaMillis =
            TimeUnit.MINUTES.toMillis(visualisationSettings.connectLinesMaxMinutesDelta)
    }

    interface OnTilesLoadedListener {
        fun onTilesLoaded()
    }

    interface OnPointsLoadedListener {
        fun onPointsLoaded()
    }

    interface OnPointProgressUpdateListener {
        fun onPointProgressMax(max: Int)

        fun onPointProgressUpdate(progress: Int)
    }
}