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
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.database.settings.VisualisationSettingsHelper
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.model.dto.VisualisationSettingsDto
import eu.tijlb.opengpslogger.model.dto.query.DATASOURCE_ALL
import eu.tijlb.opengpslogger.model.dto.query.PointsQuery
import eu.tijlb.opengpslogger.model.util.OsmGeometryUtil
import eu.tijlb.opengpslogger.ui.singleton.ImageRendererViewSingleton
import eu.tijlb.opengpslogger.ui.util.LockUtil.lockWithTimeout
import eu.tijlb.opengpslogger.ui.view.bitmap.CopyRightNoticeBitmapRenderer
import eu.tijlb.opengpslogger.ui.view.bitmap.DensityMapBitmapRenderer
import eu.tijlb.opengpslogger.ui.view.bitmap.OsmImageBitmapRenderer
import eu.tijlb.opengpslogger.ui.view.bitmap.PointsBitmapRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.seconds

private const val TAG = "ogl-imagerendererview"

class ImageRendererView(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs), CoroutineScope by MainScope() {


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
                Log.d(TAG, "Aspect ratio changed from $field to $value")
                field = value
                pointsRenderHeight = (pointsRenderWidth / aspectRatio).toInt()
                launch {
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

    var beginTime: ZonedDateTime? = null
    var endTime: ZonedDateTime? = null

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

    init {

        visualisationSettingsChangedListener =
            visualisationSettingsHelper.registerVisualisationSettingsChangedListener {
                Log.d(
                    TAG,
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
    }

    private suspend fun cancelOsmCoroutine() {
        osmLock.lockWithTimeout(30.seconds) {
            osmJob?.takeIf { it.isActive }?.cancelAndJoin()
            osmJob = null
            osmBitMap = null
        }
    }

    private suspend fun cancelCoordinateDataCoroutine() {
        coordinateDataLock.lockWithTimeout(30.seconds) {
            Log.d(TAG, "Resetting coordinate drawing drawing...")
            coordinateDataCoroutine?.takeIf { it.isActive }?.cancelAndJoin()
            coordinateDataCoroutine = null
            pointsBitMap = null
            densityMapBitMap = null
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (layoutParams == null) {
            Log.d(TAG, "Not drawing since layoutParams is null")
            return
        }
        drawMap(canvas)
        Log.d(TAG, "Finished onDraw")
    }

    override fun onDetachedFromWindow() {
        Log.d(TAG, "Detaching ImageRendererView from window...")
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
        Log.d(TAG, "Rendering source $dataSource from $beginTime till $endTime")

        if (beginTime == null || endTime == null) {
            Log.d(TAG, "No begin or end time, not drawing...")
            return
        }

        Log.d(
            TAG,
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
                        Log.i(TAG, "Redrawing coordinate data...")
                        cancelCoordinateDataCoroutine()
                        if (visualisationSettings.drawDensityMap) {
                            launchDensityMap(realBbox)
                        } else {
                            launchPointsCoroutine(realBbox)
                        }
                    }
                    if (shouldRedrawOsm) {
                        Log.i(TAG, "Redrawing osm...")
                        cancelOsmCoroutine()
                        launchOsmCoroutine(realBbox)
                    }
                }
        }

        Log.d(
            TAG,
            "Drawing osmBitmap and pointsBitmap to canvas with w ${canvas.width} h ${canvas.height}"
        )

        drawBitmap(canvas, osmBitMap)
        drawBitmap(canvas, pointsBitMap)
        drawBitmap(canvas, copyrightBitMap)
        drawBitmap(canvas, densityMapBitMap)
    }

    private fun launchDensityMap(bbox: BBoxDto) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Getting coordinateDataLock")
            coordinateDataLock.lockWithTimeout(10.seconds) {
                if (!isActive) {
                    Log.d(TAG, "Stop loading density map!")
                    return@lockWithTimeout
                }
                if (densityMapBitMap == null && coordinateDataCoroutine?.isActive != true) {
                    Log.d(TAG, "Loading density map")
                    updateVisualisationSettings()

                    if (!isActive) {
                        Log.d(TAG, "Stop drawing density map!")
                        return@lockWithTimeout
                    }

                    Log.d(TAG, "Starting coroutine for drawing density map...")
                    coordinateDataCoroutine = createDensityMapCoroutine(bbox)
                }
            }
        }
    }

    private fun createDensityMapCoroutine(bbox: BBoxDto) = CoroutineScope(Dispatchers.IO).launch {
        densityMapBitmapRenderer.draw(
            bbox,
            zoom.toDouble(),
            Pair(width, height),
            { bitmap -> densityMapBitMap = bitmap }
        ) { postInvalidate() }
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
            Log.d(TAG, "Getting coordinateDataLock")
            coordinateDataLock.lockWithTimeout(10.seconds) {
                if (!isActive) {
                    Log.d(TAG, "Stop loading points!")
                    return@lockWithTimeout
                }
                if (pointsBitMap == null && coordinateDataCoroutine?.isActive != true) {
                    Log.d(TAG, "Loading points")
                    updateVisualisationSettings()
                    val pointsQuery = pointsQuery(realBbox)

                    if (!isActive) {
                        Log.d(TAG, "Stop calculating points!")
                        return@lockWithTimeout
                    }

                    Log.d(TAG, "Starting coroutine for drawing points...")
                    coordinateDataCoroutine = createPointsCoroutine(pointsQuery)
                }
            }
        }
    }

    private fun launchOsmCoroutine(realBbox: BBoxDto) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Getting osmLock")
            osmLock.lockWithTimeout(10.seconds) {
                if (osmBitMap == null && osmJob?.isActive != true) {
                    Log.d(TAG, "Loading osm")
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
        ) { postInvalidate() }
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
                zoom.toDouble(),
                Pair(width, height),
                { bitmap -> osmBitMap = bitmap }
            )
            { postInvalidate() }
            onTilesLoadedListener?.onTilesLoaded()
            updateCopyrightNotice(bbox)
        }
    }

    private suspend fun updateCopyrightNotice(bbox: BBoxDto) {
        copyRightNoticeBitmapRenderer.draw(
            bbox,
            zoom.toDouble(),
            Pair(width, height),
            { bitmap -> copyrightBitMap = bitmap })
        { postInvalidate() }
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

    private fun getStartDateMillis() = (beginTime?.toInstant()
        ?.toEpochMilli()
        ?: 0L)

    private fun getEndDateMillis(): Long {
        val endDateMillis = endTime?.toInstant()
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