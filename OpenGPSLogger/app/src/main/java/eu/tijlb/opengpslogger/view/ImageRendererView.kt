package eu.tijlb.opengpslogger.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import eu.tijlb.opengpslogger.OsmHelper
import eu.tijlb.opengpslogger.database.location.LocationDbContract
import eu.tijlb.opengpslogger.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.database.settings.VisualisationSettingsHelper
import eu.tijlb.opengpslogger.dto.BBoxDto
import eu.tijlb.opengpslogger.dto.VisualisationSettingsDto
import eu.tijlb.opengpslogger.query.PointsQuery
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
import kotlin.math.ln
import kotlin.math.max

private const val BASEMAP_CATRODB_LIGHT =
    "https://cartodb-basemaps-b.global.ssl.fastly.net/light_all/%s/%s/%s.png"

class ImageRendererView(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {


    private var visualisationSettingsHelper: VisualisationSettingsHelper
    private var visualisationSettingsChangedListener: OnSharedPreferenceChangeListener

    var onTilesLoadedListener: OnTilesLoadedListener? = null
    var onPointsLoadedListener: OnPointsLoadedListener? = null
    var onTileProgressUpdateListener: OsmHelper.OnTileProgressUpdateListener? = null
        set(value) {
            field = value
            osmHelper.onTileProgressUpdateListener = onTileProgressUpdateListener
        }
    var onPointProgressUpdateListener: OnPointProgressUpdateListener? = null

    var aspectRatio = 1.0
        set(value) {
            if (field != value) {
                field = value
                pointsRenderHeight = (pointsRenderWidth / aspectRatio).toInt()
                CoroutineScope(Dispatchers.Main)
                    .launch {
                        layoutParams = ViewGroup.LayoutParams(width, (width / value).toInt())
                        CoroutineScope(Dispatchers.Default)
                            .launch { resetPointDrawing() }
                    }
            }
        }
    var pointsRenderWidth = width
        set(value) {
            if (value > 0) {
                field = value
                pointsRenderHeight = (pointsRenderWidth / aspectRatio).toInt()
                CoroutineScope(Dispatchers.Default)
                    .launch { resetPointDrawing() }
            }
        }
    var dataSource = "All"
    var inputBbox: BBoxDto? = null

    var beginTime: LocalDate? = null
    var endTime: LocalDate? = null

    var minAccuracy: Float? = null
    var minAngle = 0F

    private var pointsRenderHeight = height
    private val osmHelper: OsmHelper = OsmHelper()

    private val locationDbHelper: LocationDbHelper = LocationDbHelper.getInstance(getContext())

    private val millisPerMonth = 30L * 24 * 60 * 60 * 1000
    private var isDrawn = false
    private var osmBitMap: Bitmap? = null
    private var osmJob: Job? = null
    private var osmLock = Mutex()
    private var pointsBitMaps: MutableList<Bitmap?>? = null
    private var pointsCoroutines: List<Job>? = null
    private var pointsLock = Mutex()
    private var zoom = 10
    private var xMin = 0.0
    private var yMin = 0.0
    private var xRange = 1.0
    private var yRange = 1.0
    private var pointRadius = 5F
    private var visualisationSettings: VisualisationSettingsDto
    private var maxTimeDeltaMillis: Long

    init {
        visualisationSettingsHelper = VisualisationSettingsHelper(context)
        visualisationSettingsChangedListener =
            visualisationSettingsHelper.registerVisualisationSettingsChangedListener {
                Log.d(
                    "ogl-imagerendererview",
                    "Executing callback on visualisation settings changed."
                )
                visualisationSettings = it
                maxTimeDeltaMillis = TimeUnit.MINUTES.toMillis(visualisationSettings.connectLinesMaxMinutesDelta)
                CoroutineScope(Dispatchers.Default)
                    .launch {
                        resetPointDrawing()
                        invalidate()
                    }
            }
        visualisationSettings = visualisationSettingsHelper.getVisualisationSettings()
        maxTimeDeltaMillis =
            TimeUnit.MINUTES.toMillis(visualisationSettings.connectLinesMaxMinutesDelta)
    }

    fun resetIfDrawn() {
        if (isDrawn) {
            CoroutineScope(Dispatchers.IO)
                .launch { reset() }
        }
    }

    private suspend fun reset() {
        Log.d("ogl-imagerendererview", "Resetting view")
        resetOsmDrawing()
        resetPointDrawing()
        Log.d("ogl-imagerendererview", "Terminated jobs...")
        invalidate()
    }

    private suspend fun resetOsmDrawing() {
        osmLock.withLock {
            osmJob?.takeIf { it.isActive }?.cancelAndJoin()
            osmJob = null
            osmBitMap = null
        }
    }

    private suspend fun resetPointDrawing() {
        pointsLock.withLock {
            Log.d("ogl-imagerendererview", "Resetting point drawing...")
            pointsCoroutines?.filter { it.isActive }
                ?.forEach { it.cancelAndJoin() }
            pointsCoroutines = null
            pointsBitMaps = null
        }
    }

    private val dotPaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
        alpha = 125
    }
    private val linePaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
        alpha = 125
        strokeWidth = 5F
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (layoutParams == null) {
            Log.d("ogl-imagerendererview", "Not drawing since layoutParams is null")
            return
        }
        isDrawn = true
        drawMap(canvas)
        Log.d("ogl-imagerendererview", "Finished onDraw")
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        visualisationSettingsHelper.deregisterAdvancedFiltersChangedListener(
            visualisationSettingsChangedListener
        )
    }

    private fun drawMap(canvas: Canvas) {
        Log.d("ogl-imagerendererview", "Rendering source $dataSource from $beginTime till $endTime")

        if (beginTime == null || endTime == null) {
            Log.d("ogl-imagerendererview", "No begin or end time, not drawing...")
            return
        }
        val shouldLoadOsm = osmBitMap == null && osmJob?.isActive != true
        val shouldLoadTiles = pointsBitMaps == null

        Log.d(
            "ogl-imagerendererview",
            "ShouldLoadOsm $shouldLoadOsm, shouldLoadTiles $shouldLoadTiles"
        )
        if (shouldLoadOsm || shouldLoadTiles) {
            canvas.drawColor(Color.WHITE)

            launchOsmAndPointCoroutine()
        }

        Log.d(
            "ogl-imagerendererview",
            "Drawing osmBitmap and pointsBitmap to canvas with w ${canvas.width} h ${canvas.height}"
        )
        osmBitMap
            ?.let { Bitmap.createScaledBitmap(it, canvas.width, canvas.height, true) }
            ?.also { canvas.drawBitmap(it, 0f, 0f, null) }

        pointsBitMaps
            ?.filterNotNull()
            ?.map {
                Log.d(
                    "ogl-imagerendererview",
                    "Scaling bitmap from ${it.width}, ${it.height} to ${canvas.width}, ${canvas.height}"
                )
                Bitmap.createScaledBitmap(it, canvas.width, canvas.height, true)
            }
            ?.forEach { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    private fun launchOsmAndPointCoroutine() {
        CoroutineScope(Dispatchers.IO).launch {
            val realBbox =
                inputBbox ?: locationDbHelper.getCoordsRange(pointsQuery()).expand(0.05)
            calculateXYValues(realBbox)
            launchOsmCoroutine(realBbox)
            launchPointsCoroutine(realBbox)
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
                if (pointsBitMaps == null) {
                    Log.d("ogl-imagerendererview", "Loading points")
                    updateVisualisationSettings()
                    val pointThreads = 1
                    val pointsQuery = pointsQuery(realBbox)
                    pointsBitMaps = MutableList(pointThreads) { null }
                    val startDate = pointsQuery.startDateMillis
                    val endDate = pointsQuery.endDateMillis
                    val timeStep = (endDate - startDate) / pointThreads

                    Log.d("ogl-imagerendererview", "Calculating amount of points...")
                    if (!isActive) {
                        Log.d("ogl-imagerendererview", "Stop calculating points!")
                        return@launch
                    }

                    Log.d("ogl-imagerendererview", "Starting coroutine for drawing points...")
                    pointsCoroutines = (0 until pointThreads)
                        .map {
                            Pair(
                                it,
                                Pair(
                                    startDate + (it * timeStep),
                                    startDate + (it + 1) * timeStep
                                )
                            )
                        }
                        .map {
                            Pair(
                                it.first, PointsQuery(
                                    pointsQuery.dataSource,
                                    it.second.first,
                                    it.second.second,
                                    pointsQuery.bbox,
                                    pointsQuery.minAccuracy,
                                    minAngle
                                )
                            )
                        }
                        .map { createPointsCoroutine(it.second, it.first) }

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
        pointsQuery: PointsQuery,
        threadIndex: Int
    ) = CoroutineScope(Dispatchers.IO).launch {
        drawCoordinates(
            pointsQuery,
            threadIndex,
            { lat: Double -> (osmHelper.lat2num(lat, zoom) - yMin) / yRange * pointsRenderHeight },
            { lon: Double -> (osmHelper.lon2num(lon, zoom) - xMin) / xRange * pointsRenderWidth }
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
            osmHelper.getImageCluster(
                bbox,
                zoom,
                BASEMAP_CATRODB_LIGHT,
                { bitmap -> osmBitMap = bitmap }
            )
            { invalidate() }
            onTilesLoadedListener?.onTilesLoaded()
        }
    }

    private fun calculateXYValues(bbox: BBoxDto) {
        zoom = calculateZoomLevel(bbox)
        val (xmin, ymax) = osmHelper.deg2num(bbox.minLat, bbox.minLon, zoom)
        val (xmax, ymin) = osmHelper.deg2num(bbox.maxLat, bbox.maxLon, zoom)
        xMin = xmin
        yMin = ymin
        xRange = xmax - xmin
        yRange = ymax - ymin
        aspectRatio = xRange / yRange
    }

    @SuppressLint("Range")
    private suspend fun drawCoordinates(
        query: PointsQuery,
        threadIndex: Int,
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
        if (pointsBitMaps == null) {
            Log.d("ogl-imagerendererview", "PointsBitMaps is null, thread $threadIndex is stopping")
            invalidate()
            return
        }
        pointsBitMaps!![threadIndex] = clusterBitmap
        var currentMonth: Long = 0

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


                            currentMonth = draw(
                                latitude,
                                longitude,
                                prevLat,
                                prevLon,
                                time,
                                prevTime,
                                currentMonth,
                                canvas,
                                latConverter,
                                lonConverter
                            )
                            prevLat = latitude
                            prevLon = longitude
                            prevTime = time
                            if (++i >= 10000) {
                                Log.d("ogl-imagerendererview-point", "refreshing points bitmap $i")
                                onPointProgressUpdateListener?.onPointProgressUpdate(i)
                                i = 0
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
        currentMonth: Long,
        canvas: Canvas,
        latConverter: (Double) -> Double,
        lonConverter: (Double) -> Double
    ): Long {
        val monthBucket = time / millisPerMonth
        var mCurrentMonth = currentMonth
        if (monthBucket != mCurrentMonth) {
            mCurrentMonth = monthBucket
            val newColor = generateColor(monthBucket)
            dotPaint.color = newColor // todo multi threaded paint
            linePaint.color = newColor
            Log.d(
                "ogl-imagerendererview-point-color",
                "Changed color to $newColor in month $mCurrentMonth (timestamp $time)"
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

        return mCurrentMonth
    }

    private fun generateColor(seed: Long): Int {
        val random = kotlin.random.Random(seed)
        val hue = random.nextInt(0, 360)
        val saturation = 0.7f + random.nextFloat() * 0.3f
        val value = 0.8f + random.nextFloat() * 0.2f

        val hsvColor = Color.HSVToColor(floatArrayOf(hue.toFloat(), saturation, value))
        return hsvColor
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

    private fun calculateZoomLevel(bbox: BBoxDto): Int {
        val latRange = bbox.latRange()
        val lonRange = bbox.lonRange()

        val calculatedZoom = calculateZoomBasedOnRange(latRange.coerceAtLeast(lonRange))

        return calculatedZoom
    }

    private fun calculateZoomBasedOnRange(range: Double): Int {
        val maxZoom = 16
        val minZoom = 4

        val scaleFactor = 1.5 // Lower values will make the zoom decrease more slowly

        val zoom = maxZoom - (ln(range * 2 + 1) / ln(2.0) * scaleFactor)

        val finalZoom = zoom.toInt().coerceIn(minZoom, maxZoom)

        Log.d("ogl-imagerendererview-zoom", "Calculated zoom $finalZoom for range $range")
        return finalZoom
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