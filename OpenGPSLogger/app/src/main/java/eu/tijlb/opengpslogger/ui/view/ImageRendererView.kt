package eu.tijlb.opengpslogger.ui.view

import android.content.Context
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import eu.tijlb.opengpslogger.model.bitmap.SparseDensityMap
import eu.tijlb.opengpslogger.model.database.densitymap.continent.ContinentDensityMapDbContract
import eu.tijlb.opengpslogger.model.database.densitymap.continent.ContinentDensityMapDbHelper
import eu.tijlb.opengpslogger.model.database.densitymap.continent.SUBDIVISIONS_CONTINENT
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.database.settings.VisualisationSettingsHelper
import eu.tijlb.opengpslogger.model.database.tileserver.TileServerDbHelper
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.model.dto.VisualisationSettingsDto
import eu.tijlb.opengpslogger.model.dto.query.DATASOURCE_ALL
import eu.tijlb.opengpslogger.model.dto.query.PointsQuery
import eu.tijlb.opengpslogger.model.util.ColorUtil
import eu.tijlb.opengpslogger.model.util.OsmGeometryUtil
import eu.tijlb.opengpslogger.ui.singleton.ImageRendererViewSingleton
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
import kotlin.coroutines.coroutineContext
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.tan
import androidx.core.graphics.scale
import eu.tijlb.opengpslogger.ui.view.bitmap.CopyRightNoticeBitmapRenderer

class ImageRendererView(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {


    private var visualisationSettingsHelper: VisualisationSettingsHelper =
        VisualisationSettingsHelper(context)
    private var visualisationSettingsChangedListener: OnSharedPreferenceChangeListener
    private var tileServerDbHelper: TileServerDbHelper = TileServerDbHelper(context)

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
    private val copyRightNoticeBitmapRenderer: CopyRightNoticeBitmapRenderer = CopyRightNoticeBitmapRenderer()
    private val pointsBitmapRenderer: PointsBitmapRenderer

    private val locationDbHelper: LocationDbHelper = LocationDbHelper.getInstance(getContext())
    private val continentDensityMapDbHelper: ContinentDensityMapDbHelper =
        ContinentDensityMapDbHelper.getInstance(getContext())

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
        Log.d("ogl-imagerendererview", "Detaching from window...")
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
        drawDensityMap(bbox)
    }

    private suspend fun drawDensityMap(
        bbox: BBoxDto
    ) {
        Log.d("ogl-imagerendererview", "Drawing density map...")
        if (!coroutineContext.isActive) {
            Log.d("ogl-imagerendererview", "Stop drawing density map!")
            invalidate()
            return
        }

        val sparseDensityMap = SparseDensityMap(SUBDIVISIONS_CONTINENT, SUBDIVISIONS_CONTINENT)
        var adaptedClusterBitmap =
            Bitmap.createBitmap(pointsRenderWidth, pointsRenderHeight, Bitmap.Config.ARGB_8888)

        densityMapBitMap = adaptedClusterBitmap

        var i = 0
        continentDensityMapDbHelper.getAllPoints()
            .use { cursor ->
                run {
                    if (!coroutineContext.isActive) {
                        Log.d("ogl-imagerendererview-point", "Stop drawing density map!")
                        invalidate()
                        return
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
                            cursor.getColumnIndex(ContinentDensityMapDbContract.COLUMN_NAME_X_INDEX)
                        val yIndexColumnIndex =
                            cursor.getColumnIndex(ContinentDensityMapDbContract.COLUMN_NAME_Y_INDEX)
                        val timeColumnIndex =
                            cursor.getColumnIndex(ContinentDensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME)
                        val countColumnIndex =
                            cursor.getColumnIndex(ContinentDensityMapDbContract.COLUMN_NAME_AMOUNT)

                        Log.d("ogl-imagerendererview-point", "Got first point from cursor")
                        do {
                            if (!coroutineContext.isActive) {
                                Log.d("ogl-imagerendererview-point", "Stop drawing density map!")
                                invalidate()
                                return
                            }

                            val xIndex = cursor.getFloat(xIndexColumnIndex)
                            val yIndex = cursor.getFloat(yIndexColumnIndex)
                            val time = cursor.getLong(timeColumnIndex)
                            val amount = cursor.getLong(countColumnIndex)

                            // TODO set the max correctly
                            val color = ColorUtil.toDensityColor(amount, 10000L)
                            if (xIndex >= 0 && xIndex <= sparseDensityMap.width && yIndex >= 0 && yIndex <= sparseDensityMap.height) {
                                sparseDensityMap.put(xIndex, yIndex, color)
                            }

                            if ((++i) % 10000 == 0) {
                                Log.d(
                                    "ogl-imagerendererview-point",
                                    "refreshing density map bitmap $i"
                                )
                                densityMapBitMap = extractAndScaleBitmap(
                                    sparseDensityMap,
                                    adaptedClusterBitmap,
                                    bbox
                                )
                                onPointProgressUpdateListener?.onPointProgressUpdate(i)
                                invalidate()
                            }

                        } while (cursor.moveToNext())
                    }
                }
            }

        densityMapBitMap = extractAndScaleBitmap(sparseDensityMap, adaptedClusterBitmap, bbox, true)
        onPointProgressUpdateListener?.onPointProgressUpdate(i)
        Log.d("ogl-imagerendererview-point", "Done drawing density map...")
        invalidate()
        onPointsLoadedListener?.onPointsLoaded()
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

                canvas.drawRect(
                    mappedX - cellWidth / 2F,
                    mappedY - cellHeight / 2F,
                    mappedX + cellWidth / 2F,
                    mappedY + cellHeight / 2F,
                    paint
                )
            }
        }

        var result = targetBitmap
        if (blur) {
            val minCellDimension = min(cellWidth, cellHeight)
            val passes = (minCellDimension * 0.5F).toInt().coerceIn(1, 10)
            result = blurBitmapMultiplePasses(
                context,
                targetBitmap,
                radius = minCellDimension * 0.5F,
                passes = passes
            )
        }
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

                    Log.d("ogl-imagerendererview", "Calculating amount of points...")
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
            val clusterBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            osmBitMap = clusterBitmap
            osmImageBitmapRenderer.draw(
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
        copyRightNoticeBitmapRenderer.draw(
            copyrightNotice,
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