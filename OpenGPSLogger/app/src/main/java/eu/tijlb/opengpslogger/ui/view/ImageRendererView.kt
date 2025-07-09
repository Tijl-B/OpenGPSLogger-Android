package eu.tijlb.opengpslogger.ui.view

import android.content.Context
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.lifecycle.LifecycleOwner
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.database.settings.VisualisationSettingsHelper
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.model.dto.VisualisationSettingsDto
import eu.tijlb.opengpslogger.model.dto.query.DATASOURCE_ALL
import eu.tijlb.opengpslogger.model.dto.query.PointsQuery
import eu.tijlb.opengpslogger.model.util.OsmGeometryUtil
import eu.tijlb.opengpslogger.ui.singleton.ImageRendererViewSingleton
import eu.tijlb.opengpslogger.ui.view.bitmap.CopyRightNoticeBitmapRenderer
import eu.tijlb.opengpslogger.ui.view.bitmap.DensityMapBitmapRenderer
import eu.tijlb.opengpslogger.ui.view.bitmap.OsmImageBitmapRenderer
import eu.tijlb.opengpslogger.ui.view.bitmap.PointsBitmapRenderer
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

class ImageRendererView(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {


    private var visualisationSettingsHelper: VisualisationSettingsHelper =
        VisualisationSettingsHelper(context)
    private var visualisationSettingsChangedListener: OnSharedPreferenceChangeListener

    var onTilesLoadedListener: OnTilesLoadedListener? = null
    var onPointsLoadedListener: OnPointsLoadedListener? = null
    var onTileProgressUpdateListener: OsmImageBitmapRenderer.OnTileProgressUpdateListener? = null
        set(value) {
            field = value
            osmImageBitmapRenderer.onTileProgressUpdateListener = value
        }
    var onPointProgressUpdateListener: PointsBitmapRenderer.OnPointProgressUpdateListener? = null
        set(value) {
            field = value
            pointsBitmapRenderer.onPointProgressUpdateListener = value
            densityMapBitmapRenderer.onPointProgressUpdateListener = value
        }

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

    var dataSource = DATASOURCE_ALL
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
    var redrawCoordinateData = false
        set(value) {
            field = value
            if (value) {
                invalidate()
            }
        }

    private var pointsRenderHeight = height

    private val osmImageBitmapRenderer: OsmImageBitmapRenderer = OsmImageBitmapRenderer(context)
    private val densityMapBitmapRenderer: DensityMapBitmapRenderer =
        DensityMapBitmapRenderer(context)
    private val copyRightNoticeBitmapRenderer: CopyRightNoticeBitmapRenderer =
        CopyRightNoticeBitmapRenderer(context)
    private val pointsBitmapRenderer: PointsBitmapRenderer

    private val locationDbHelper: LocationDbHelper = LocationDbHelper.getInstance(getContext())

    private var osmBitMap: Bitmap? = null
    private var osmJob: Job? = null
    private var osmLock = Mutex()
    private var pointsBitMap: Bitmap? = null
    private var densityMapBitMap: Bitmap? = null
    private var coordinateDataCoroutine: Job? = null
    private var coordinateDataLock = Mutex()
    private var copyrightBitMap: Bitmap? = null
    private var zoom = 10
    private var xMin = 0.0
    private var yMin = 0.0
    private var xRange = 1.0
    private var yRange = 1.0
    private var visualisationSettings: VisualisationSettingsDto =
        visualisationSettingsHelper.getVisualisationSettings()
        set(value) {
            field = value
            pointsBitmapRenderer?.visualisationSettings = value
        }

    private var initialised = false

    init {

        visualisationSettingsChangedListener =
            visualisationSettingsHelper.registerVisualisationSettingsChangedListener {
                Log.d(
                    "ogl-imagerendererview",
                    "Executing callback on visualisation settings changed."
                )
                visualisationSettings = it
                redrawCoordinateData = true
            }
        visualisationSettings = visualisationSettingsHelper.getVisualisationSettings()
        ImageRendererViewSingleton.registerView(this)
        pointsBitmapRenderer = PointsBitmapRenderer(context, visualisationSettings)
    }


    fun redrawPointsAndOsm() {
        redrawOsm = true
        redrawCoordinateData = true
        invalidate()
    }

    private suspend fun cancelOsmCoroutine() {
        osmLock.withLock {
            osmJob?.takeIf { it.isActive }?.cancelAndJoin()
            osmJob = null
            osmBitMap = null
        }
    }

    private suspend fun cancelCoordinateDataCoroutine() {
        coordinateDataLock.withLock {
            Log.d("ogl-imagerendererview", "Resetting coordinate drawing drawing...")
            coordinateDataCoroutine?.takeIf { it.isActive }?.cancelAndJoin()
            coordinateDataCoroutine = null
            pointsBitMap = null
            densityMapBitMap = null
        }
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
        Log.d("ogl-imagerendererview", "Detaching ImageRendererView from window...")
        super.onDetachedFromWindow()
        visualisationSettingsHelper.deregisterAdvancedFiltersChangedListener(
            visualisationSettingsChangedListener
        )
        CoroutineScope(Dispatchers.IO)
            .launch {
                cancelOsmCoroutine()
                cancelCoordinateDataCoroutine()
            }
    }

    private fun drawMap(canvas: Canvas) {
        Log.d("ogl-imagerendererview", "Rendering source $dataSource from $beginTime till $endTime")

        if (beginTime == null || endTime == null) {
            Log.d("ogl-imagerendererview", "No begin or end time, not drawing...")
            return
        }

        Log.d(
            "ogl-imagerendererview",
            "redrawOsm $redrawOsm, redrawCoordinateData $redrawCoordinateData"
        )

        if (redrawCoordinateData || redrawOsm) {
            val shouldRedrawCoordinateData = redrawCoordinateData
            val shouldRedrawOsm = redrawOsm
            redrawCoordinateData = false
            redrawOsm = false
            CoroutineScope(Dispatchers.IO)
                .launch {
                    val realBbox = inputBbox
                        ?: locationDbHelper.getCoordsRange(pointsQuery())
                            .expand(0.05)
                    calculateXYValues(realBbox)

                    if (shouldRedrawCoordinateData) {
                        Log.d("ogl-imagerendererview", "Redrawing coordinate data...")
                        cancelCoordinateDataCoroutine()
                        if (visualisationSettings.drawDensityMap) {
                            launchDensityMap(realBbox)
                        } else {
                            launchPointsCoroutine(realBbox)
                        }
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
        drawBitmap(canvas, densityMapBitMap)
    }

    private fun launchDensityMap(bbox: BBoxDto) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d("ogl-imagerendererview", "Getting coordinateDataLock")
            coordinateDataLock.withLock {
                if (!isActive) {
                    Log.d("ogl-imagerendererview", "Stop loading density map!")
                    return@withLock
                }
                if (densityMapBitMap == null && coordinateDataCoroutine?.isActive != true) {
                    Log.d("ogl-imagerendererview", "Loading density map")
                    updateVisualisationSettings()

                    if (!isActive) {
                        Log.d("ogl-imagerendererview", "Stop drawing density map!")
                        return@withLock
                    }

                    Log.d("ogl-imagerendererview", "Starting coroutine for drawing density map...")
                    coordinateDataCoroutine = createDensityMapCoroutine(bbox)
                }
            }
        }
    }

    private fun createDensityMapCoroutine(bbox: BBoxDto) = CoroutineScope(Dispatchers.IO).launch {
        densityMapBitmapRenderer.draw(
            bbox,
            zoom,
            Pair(width, height),
            { bitmap -> densityMapBitMap = bitmap }
        ) { invalidate() }
        onPointsLoadedListener?.onPointsLoaded()
    }

    private fun latToPxIdxConverter(lat: Double) =
        (OsmGeometryUtil.lat2num(lat, zoom) - yMin) / yRange * pointsRenderHeight

    private fun lonToPxIdxConverter(lon: Double) =
        (OsmGeometryUtil.lon2num(lon, zoom) - xMin) / xRange * pointsRenderWidth

    private fun drawBitmap(canvas: Canvas, bitMap: Bitmap?) {
        bitMap?.scale(canvas.width, canvas.height)
            ?.also {
                canvas.drawBitmap(it, 0f, 0f, null)
            }
    }

    private fun launchPointsCoroutine(realBbox: BBoxDto) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d("ogl-imagerendererview", "Getting coordinateDataLock")
            coordinateDataLock.withLock {
                if (!isActive) {
                    Log.d("ogl-imagerendererview", "Stop loading points!")
                    return@withLock
                }
                if (pointsBitMap == null && coordinateDataCoroutine?.isActive != true) {
                    Log.d("ogl-imagerendererview", "Loading points")
                    updateVisualisationSettings()
                    val pointsQuery = pointsQuery(realBbox)

                    if (!isActive) {
                        Log.d("ogl-imagerendererview", "Stop calculating points!")
                        return@withLock
                    }

                    Log.d("ogl-imagerendererview", "Starting coroutine for drawing points...")
                    coordinateDataCoroutine = createPointsCoroutine(pointsQuery)
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
        pointsBitmapRenderer.draw(
            pointsQuery,
            ::latToPxIdxConverter,
            ::lonToPxIdxConverter,
            Pair(pointsRenderWidth, pointsRenderHeight),
            { bitmap -> pointsBitMap = bitmap }
        ) { invalidate() }
        onPointsLoadedListener?.onPointsLoaded()
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
            val clusterBitmap = createBitmap(width, height)
            osmBitMap = clusterBitmap
            osmImageBitmapRenderer.draw(
                bbox,
                zoom,
                Pair(width, height),
                { bitmap -> osmBitMap = bitmap }
            )
            { invalidate() }
            onTilesLoadedListener?.onTilesLoaded()
            updateCopyrightNotice(bbox)
        }
    }

    private suspend fun updateCopyrightNotice(bbox: BBoxDto) {
        copyRightNoticeBitmapRenderer.draw(
            bbox,
            zoom,
            Pair(width, height),
            { bitmap -> copyrightBitMap = bitmap })
        { invalidate() }
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
    }

    interface OnTilesLoadedListener {
        fun onTilesLoaded()
    }

    interface OnPointsLoadedListener {
        fun onPointsLoaded()
    }
}