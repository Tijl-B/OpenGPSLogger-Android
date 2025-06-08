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
import androidx.core.graphics.withTranslation
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.model.util.OsmGeometryUtil
import eu.tijlb.opengpslogger.ui.view.bitmap.DensityMapBitmapRenderer
import eu.tijlb.opengpslogger.ui.view.bitmap.OsmImageBitmapRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.pow
import androidx.core.graphics.createBitmap
import eu.tijlb.opengpslogger.model.database.tileserver.TileServerDbHelper
import kotlinx.coroutines.Job

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

    private var osmBitmap: Bitmap? = null
    private var pointsBitmap: Bitmap? = null
    private var centerLat = 0.0
    private var centerLon = 0.0
    private var zoomLevel = 4f

    private var offsetX = 0f
    private var offsetY = 0f
    private var lastScaleFocusX = 0f
    private var lastScaleFocusY = 0f

    private var visualZoomScale = 1f

    private var needsRedraw = false
    private var coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var osmJob: Job? = null
    private var pointsJob: Job? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Log.d("ogl-osmmapview", "onDraw OsmMapView with bitmap? ${osmBitmap != null}")
        osmBitmap?.let {
            canvas.withTranslation(offsetX, offsetY) {
                val intZoom = zoomLevel.toInt()
                val scaleBetweenLevels = 2.0.pow((zoomLevel - intZoom).toDouble())
                scale(
                    visualZoomScale * scaleBetweenLevels.toFloat(),
                    visualZoomScale * scaleBetweenLevels.toFloat(),
                    width / 2f,
                    height / 2f
                )
                drawBitmap(it, 0f, 0f, null)
                pointsBitmap?.let {
                    drawBitmap(it, 0f, 0f, null)
                }

            }
        } ?: loadTilesAndPoints()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        scaleGestureDetector.onTouchEvent(event)

        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            redrawIfNeeded()
        }

        return true
    }

    private fun redrawIfNeeded() {
        if (needsRedraw) {
            commitZoom()
            commitPan()
            needsRedraw = false
            pointsJob?.cancel()
            osmJob?.cancel()
            loadTilesAndPoints()
        }
    }

    private fun loadTilesAndPoints() {
        Log.d("ogl-osmmapview", "Loading tiles $centerLat, $centerLon, $width, $height")
        val intZoom = zoomLevel.toInt()
        val bbox = bboxFromCenter(centerLat, centerLon, intZoom, width, height)
        osmJob = coroutineScope.launch {
            triggerTileDraw(bbox, intZoom)
        }
        pointsJob = coroutineScope.launch {
            densityMapBitmapRenderer.draw(
                bbox,
                intZoom,
                Pair(width, height),
                { bmp -> pointsBitmap = bmp },
                { invalidate() }
            )
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

    // -- Gesture Handling --

    override fun onDown(e: MotionEvent) = true

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        Log.d("ogl-osmmapview", "scrolling...")
        panMap(distanceX, distanceY)
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        lastScaleFocusX = detector.focusX
        lastScaleFocusY = detector.focusY

        visualZoomScale *= detector.scaleFactor
        visualZoomScale = visualZoomScale.coerceIn(0.5f, 3f)
        needsRedraw = true
        invalidate()
        return true
    }


    private fun commitZoom() {
        Log.d("ogl-osmmapview", "Commit visualZoomScale to $visualZoomScale")

        val tileSize = 256.0
        val oldZoomInt = zoomLevel.toInt()

        val oldMapScale = 2.0.pow(zoomLevel.toDouble())

        val newMapScale = (oldMapScale * visualZoomScale)
            .coerceIn(2.0.pow(MIN_ZOOM), 2.0.pow(MAX_ZOOM))

        val newZoomFloat = ln(newMapScale) / ln(2.0)
        val newZoomInt = newZoomFloat.toInt()

        val centerPixelXOldZoom = OsmGeometryUtil.lon2num(centerLon, oldZoomInt) * tileSize
        val centerPixelYOldZoom = OsmGeometryUtil.lat2num(centerLat, oldZoomInt) * tileSize

        val centerLon = OsmGeometryUtil.numToLon(centerPixelXOldZoom / tileSize, oldZoomInt)
        val centerLat = OsmGeometryUtil.numToLat(centerPixelYOldZoom / tileSize, oldZoomInt)

        val focusPixelXNewZoom = OsmGeometryUtil.lon2num(centerLon, newZoomInt) * tileSize
        val focusPixelYNewZoom = OsmGeometryUtil.lat2num(centerLat, newZoomInt) * tileSize

        this.centerLon = OsmGeometryUtil.numToLon(focusPixelXNewZoom / tileSize, newZoomInt)
        this.centerLat = OsmGeometryUtil.numToLat(focusPixelYNewZoom / tileSize, newZoomInt)

        zoomLevel = newZoomFloat.toFloat()
        zoomOsmBitmap()
        zoomPointsBitmap()

        visualZoomScale = 1f
    }

    private fun panMap(dx: Float, dy: Float) {
        offsetX -= dx
        offsetY -= dy
        needsRedraw = true
        invalidate()
    }


    private fun commitPan() {
        val tileSize = 256.0
        val zoom = zoomLevel.toInt()
        val scale = 2.0.pow(zoom)
        val mapSize = tileSize * scale

        val deltaX = -offsetX
        val deltaY = -offsetY

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

        centerLon = OsmGeometryUtil.numToLon(newCenterPixelX / tileSize, zoom)
        centerLat = OsmGeometryUtil.numToLat(newCenterPixelY / tileSize, zoom)

        panOsmBitmap()
        panPointsBitmap()

        offsetX = 0f
        offsetY = 0f
    }

    private fun panOsmBitmap() {
        osmBitmap?.let {
            osmBitmap = panBitmap(it)
            invalidate()
        }
    }

    private fun zoomOsmBitmap() {
        osmBitmap?.let {
            osmBitmap = zoomBitmap(it)
            invalidate()
        }
    }

    private fun panPointsBitmap() {
        pointsBitmap?.let {
            pointsBitmap = panBitmap(it)
            invalidate()
        }
    }

    private fun zoomPointsBitmap() {
        pointsBitmap?.let {
            pointsBitmap = zoomBitmap(it)
            invalidate()
        }
    }

    private fun panBitmap(it: Bitmap): Bitmap {
        val pannedBitMap = createBitmap(it.width, it.height)
        val canvas = Canvas(pannedBitMap)

        val matrix = Matrix().apply {
            postTranslate(offsetX, offsetY)
        }

        canvas.drawBitmap(it, matrix, null)
        return pannedBitMap
    }

    private fun zoomBitmap(bitMap: Bitmap): Bitmap {
        val zoomedBitmap = createBitmap(bitMap.width, bitMap.height)
        val canvas = Canvas(zoomedBitmap)

        val matrix = Matrix().apply {
            postScale(visualZoomScale, visualZoomScale, bitMap.width / 2f, bitMap.height / 2f)
        }

        canvas.drawBitmap(bitMap, matrix, null)
        return zoomedBitmap
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
