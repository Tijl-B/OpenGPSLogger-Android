package eu.tijlb.opengpslogger.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.OnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import eu.tijlb.opengpslogger.model.database.settings.BrowseSettingsHelper
import eu.tijlb.opengpslogger.model.database.tileserver.TileServerDbHelper
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.model.util.OsmGeometryUtil
import eu.tijlb.opengpslogger.ui.view.bitmap.DensityMapBitmapRenderer
import eu.tijlb.opengpslogger.ui.view.bitmap.OsmImageBitmapRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.log2
import kotlin.math.pow

private const val MIN_ZOOM = 4.0
private const val MAX_ZOOM = 20.0

class OsmMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs), OnGestureListener,
    ScaleGestureDetector.OnScaleGestureListener {

    private val gestureDetector = GestureDetector(context, this)
    private val scaleGestureDetector = ScaleGestureDetector(context, this)
    private val osmImageBitmapRenderer = OsmImageBitmapRenderer(context)
    private val densityMapBitmapRenderer = DensityMapBitmapRenderer(context)
    private val tileServerDbHelper: TileServerDbHelper = TileServerDbHelper(context)
    private val browseSettingsHelper = BrowseSettingsHelper(context)

    private var osmBitmap: Bitmap? = null
    private var pointsBitmap: Bitmap? = null
    private var centerLat = 0.0
    private var centerLon = 0.0
    private var osmBitmapZoomLevel = 4
    private var pointsBitmapZoomLevel = 4

    private var offsetX = 0f
    private var offsetY = 0f
    private var visualZoomLevel = 4.0

    private var needsRedraw = true
    private var osmJob: Job? = null
    private var pointsJob: Job? = null

    private val redrawMutex = Mutex()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Log.d("ogl-osmmapview", "onDraw OsmMapView with bitmap? ${osmBitmap != null}")
        osmBitmap?.let {
            translateAndScale(canvas, it, osmBitmapZoomLevel)
        } ?: run {
            setUpCenterAndZoom()
            redrawIfNeeded()
        }
        pointsBitmap?.let {
            translateAndScale(canvas, it, pointsBitmapZoomLevel)
        }
    }

    private fun translateAndScale(canvas: Canvas, bitmap: Bitmap, zoom: Int) {
        canvas.withTranslation(offsetX, offsetY) {
            val scaleAmount = calculateScaleAmount(zoom)
            scale(
                scaleAmount,
                scaleAmount,
                width / 2F,
                height / 2F
            )
            Log.d(
                "ogl-osmmapview",
                "Drawing bitmap with translation x $offsetX y $offsetY and scale $scaleAmount"
            )
            drawBitmap(bitmap, 0f, 0f, null)
        }
    }

    private fun setUpCenterAndZoom() {
        val centerCoords = browseSettingsHelper.getCenterCoords()
        centerLat = centerCoords.first
        centerLon = centerCoords.second
        visualZoomLevel = browseSettingsHelper.getZoom().toDouble()
    }

    private fun redrawIfNeeded() {
        CoroutineScope(Dispatchers.IO).launch {
            redrawMutex.withLock {
                if (needsRedraw) {
                    needsRedraw = false
                    pointsJob?.cancel()
                    osmJob?.cancel()
                    commitPan()
                    commitZoom()
                    invalidate()
                    loadTilesAndPoints()
                    browseSettingsHelper.setCenterCoords(centerLat, centerLon)
                    browseSettingsHelper.setZoom(visualZoomLevel.toFloat())
                }
            }
        }
    }

    private fun loadTilesAndPoints() {
        Log.d("ogl-osmmapview", "Loading tiles $centerLat, $centerLon, $width, $height")
        val newBitmapZoom = visualZoomLevel.toInt()
        val bbox = bboxFromCenter(centerLat, centerLon, newBitmapZoom, width, height)
        osmJob = CoroutineScope(Dispatchers.IO).launch {
            triggerTileDraw(bbox, newBitmapZoom)
        }
        pointsJob = CoroutineScope(Dispatchers.IO).launch {
            triggerPointsDraw(bbox, newBitmapZoom)
        }
    }

    private suspend fun triggerPointsDraw(bbox: BBoxDto, intZoom: Int) {
        densityMapBitmapRenderer.draw(
            bbox,
            intZoom,
            Pair(width, height),
            { },
            { }
        )?.let {
            pointsBitmap = it
            invalidate()
        }
    }

    private suspend fun triggerTileDraw(bbox: BBoxDto, intZoom: Int) {
        var tmpBitmap: Bitmap? = null
        val tileServer = tileServerDbHelper.getSelectedUrl()
        osmImageBitmapRenderer.draw(
            bbox, intZoom, tileServer,
            { bmp -> tmpBitmap = bmp },
            {
                osmBitmap?.let {
                    val canvas = Canvas(it)
                    tmpBitmap?.let { bitmap ->
                        canvas.drawBitmap(bitmap, 0F, 0F, null)
                    }
                } ?: run { osmBitmap = tmpBitmap }
                invalidate()
            }
        )
    }

    private fun bboxFromCenter(
        lat: Double,
        lon: Double,
        zoom: Int,
        viewWidth: Int,
        viewHeight: Int
    ): BBoxDto {
        val tileSize = 256

        val centerX = OsmGeometryUtil.lon2num(lon, zoom) * tileSize
        val centerY = OsmGeometryUtil.lat2num(lat, zoom) * tileSize

        val halfWidth = viewWidth / 2.0
        val halfHeight = viewHeight / 2.0

        val minPxX = centerX - halfWidth
        val maxPxX = centerX + halfWidth
        val minPxY = centerY - halfHeight
        val maxPxY = centerY + halfHeight

        val minTileX = minPxX / tileSize
        val maxTileX = maxPxX / tileSize
        val minTileY = minPxY / tileSize
        val maxTileY = maxPxY / tileSize

        val minLon = OsmGeometryUtil.numToLon(minTileX, zoom)
        val maxLon = OsmGeometryUtil.numToLon(maxTileX, zoom)
        val minLat = OsmGeometryUtil.numToLat(maxTileY, zoom)
        val maxLat = OsmGeometryUtil.numToLat(minTileY, zoom)

        return BBoxDto(minLat, maxLat, minLon, maxLon)
    }

    private fun scaleMap(scaleFactor: Float) {
        visualZoomLevel = calculateNewZoom(scaleFactor)
            .coerceIn(MIN_ZOOM, MAX_ZOOM)

        if (visualZoomLevel.toInt() != osmBitmapZoomLevel || visualZoomLevel.toInt() != pointsBitmapZoomLevel) {
            needsRedraw = true
            redrawIfNeeded()
        }
        invalidate()
    }

    private fun commitZoom() {
        Log.d("ogl-osmmapview", "Commit visualZoomScale to $visualZoomLevel")
        val oldZoom = osmBitmapZoomLevel
        val newZoom = visualZoomLevel.toInt()

        osmBitmap = osmBitmap?.let {
            zoomBitmap(it, oldZoom, newZoom)
        }
        osmBitmapZoomLevel = newZoom

        pointsBitmap = pointsBitmap?.let {
            zoomBitmap(it, oldZoom, newZoom)
        }
        pointsBitmapZoomLevel = newZoom
    }

    private fun calculateNewZoom(scale: Float): Double {
        val oldMapScale = 2.0.pow(visualZoomLevel.toDouble())
        val newMapScale = (oldMapScale * scale)
        val newZoom = log2(newMapScale)
        return newZoom
    }

    private fun panMap(dx: Float, dy: Float) {
        offsetX -= dx
        offsetY -= dy
        needsRedraw = true
        invalidate()
    }

    private fun commitPan() {
        val tileSize = 256.0
        val zoom = osmBitmapZoomLevel
        val scale = 2.0.pow(zoom)
        val mapSize = tileSize * scale

        val amountToPanX = -offsetX
        val amountToPanY = -offsetY

        val deltaX = amountToPanX / calculateScaleAmount(zoom)
        val deltaY = amountToPanY / calculateScaleAmount(zoom)

        val centerPixelX = OsmGeometryUtil.lon2num(centerLon, zoom) * tileSize
        val centerPixelY = OsmGeometryUtil.lat2num(centerLat, zoom) * tileSize

        var newCenterPixelX = centerPixelX + deltaX
        var newCenterPixelY = centerPixelY + deltaY

        val halfWidth = width / 2.0
        val halfHeight = height / 2.0

        val minCenterX = halfWidth
        val maxCenterX = mapSize - halfWidth
        val minCenterY = halfHeight
        val maxCenterY = mapSize - halfHeight

        newCenterPixelX = newCenterPixelX.coerceIn(minCenterX, maxCenterX)
        newCenterPixelY = newCenterPixelY.coerceIn(minCenterY, maxCenterY)

        val newOsmBitmap = osmBitmap?.let {
            panBitmap(it, osmBitmapZoomLevel, amountToPanX, amountToPanY)
        }
        val newPointsBitmap = pointsBitmap?.let {
            panBitmap(it, pointsBitmapZoomLevel, amountToPanX, amountToPanY)
        }

        centerLon = OsmGeometryUtil.numToLon(newCenterPixelX / tileSize, zoom)
        centerLat = OsmGeometryUtil.numToLat(newCenterPixelY / tileSize, zoom)
        osmBitmap = newOsmBitmap
        pointsBitmap = newPointsBitmap
        offsetX += amountToPanX
        offsetY += amountToPanY
    }

    private fun panBitmap(it: Bitmap, zoom: Int, amountToPanX: Float, amountToPanY: Float): Bitmap {
        val pannedBitMap = createBitmap(it.width, it.height)
        val canvas = Canvas(pannedBitMap)

        val deltaX = -amountToPanX / calculateScaleAmount(zoom)
        val deltaY = -amountToPanY / calculateScaleAmount(zoom)

        Log.d("ogl-osmmapview", "Panning bitmap with offset x $amountToPanX and y $amountToPanY")
        val matrix = Matrix().apply {
            setTranslate(deltaX, deltaY)
        }

        canvas.drawBitmap(it, matrix, null)
        return pannedBitMap
    }

    private fun zoomBitmap(bitMap: Bitmap, oldZoom: Int, newZoom: Int): Bitmap {
        if (oldZoom == newZoom)
            return bitMap

        Log.d("ogl-osmmapview", "Zooming bitmap from $oldZoom to $newZoom")

        val zoomedBitmap = createBitmap(bitMap.width, bitMap.height)
        val canvas = Canvas(zoomedBitmap)

        val scale = 2.0F.pow((newZoom - oldZoom))
        Log.d(
            "ogl-osmmapview",
            "Need to scale by $scale to go from zoom $oldZoom to $newZoom"
        )

        val matrix = Matrix().apply {
            setScale(scale, scale, bitMap.width / 2f, bitMap.height / 2f)
        }

        canvas.drawBitmap(bitMap, matrix, null)
        return zoomedBitmap
    }


    private fun calculateScaleAmount(zoom: Int): Float {
        val scaleBetweenLevels = 2.0.pow((visualZoomLevel - zoom))
        Log.d(
            "ogl-osmmapview",
            "$scaleBetweenLevels = 2^($osmBitmapZoomLevel - $zoom)"
        )
        return scaleBetweenLevels.toFloat()
    }

    // -- Gesture Handling --

    override fun onDown(e: MotionEvent) = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        scaleGestureDetector.onTouchEvent(event)

        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            redrawIfNeeded()
        }
        return true
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        Log.d("ogl-osmmapview", "scrolling x $distanceX y $distanceY (${e1?.action}, ${e2.action})")
        panMap(distanceX, distanceY)
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        Log.d("ogl-osmmapview", "scaling with factor ${detector.scaleFactor}")
        val scaleFactor = detector.scaleFactor
        scaleMap(scaleFactor)
        return true
    }

    // Unused but required GestureDetector methods
    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent) = false

    override fun onLongPress(e: MotionEvent) {}
    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ) = false

    override fun onScaleBegin(detector: ScaleGestureDetector) = true
    override fun onScaleEnd(detector: ScaleGestureDetector) {}
}
