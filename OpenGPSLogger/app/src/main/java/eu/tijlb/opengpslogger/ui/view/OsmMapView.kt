package eu.tijlb.opengpslogger.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.GestureDetector.OnGestureListener
import android.view.View
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.model.util.OsmGeometryUtil
import eu.tijlb.opengpslogger.ui.view.bitmap.OsmImageBitmapRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.pow
import androidx.core.graphics.withTranslation
import eu.tijlb.opengpslogger.ui.view.bitmap.DensityMapBitmapRenderer

class OsmMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs), OnGestureListener,
    ScaleGestureDetector.OnScaleGestureListener {

    private val gestureDetector = GestureDetector(context, this)
    private val scaleGestureDetector = ScaleGestureDetector(context, this)
    private val osmImageBitmapRenderer = OsmImageBitmapRenderer(context)
    private val densityMapBitmapRenderer = DensityMapBitmapRenderer(context)

    private var osmBitmap: Bitmap? = null
    private var pointsBitmap: Bitmap? = null
    private var centerLat = 0.0
    private var centerLon = 0.0
    private var zoomLevel = 2f

    private var offsetX = 0f
    private var offsetY = 0f
    private var lastScaleFocusX = 0f
    private var lastScaleFocusY = 0f


    private var visualZoomScale = 1f

    private var needsRedraw = false


    private var coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var tileServerUrl: String =
        "https://cartodb-basemaps-b.global.ssl.fastly.net/light_all/{z}/{x}/{y}.png"

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
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        scaleGestureDetector.onTouchEvent(event)

        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            if (needsRedraw) {
                commitPan()
                commitZoom()
                needsRedraw = false
                loadTiles()
            }
        }

        return true
    }

    private fun loadTiles() {
        Log.d("ogl-osmmapview", "Loading tiles $centerLat, $centerLon, $width, $height")
        val intZoom = zoomLevel.toInt()
        val bbox = bboxFromCenter(centerLat, centerLon, intZoom, width, height)
        coroutineScope.launch {
            osmImageBitmapRenderer.draw(
                bbox,
                intZoom,
                tileServerUrl,
                { bmp -> osmBitmap = bmp },
                { invalidate() }
            )
        }
        coroutineScope.launch {
            densityMapBitmapRenderer.draw(
                bbox,
                Pair(width, height),
                { bmp -> pointsBitmap = bmp },
                { invalidate() }
            )
        }
    }

    private fun bboxFromCenter(
        lat: Double,
        lon: Double,
        zoom: Int,
        viewWidth: Int,
        viewHeight: Int
    ): BBoxDto {
        val tileSize = 256
        val scale = 2.0.pow(zoom)

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

        // Current map scale (pixels per tile at current zoom)
        val oldMapScale = 2.0.pow(zoomLevel.toDouble())

        // New map scale after applying visual zoom scale, constrained within zoom limits
        val newMapScale = (oldMapScale * visualZoomScale).coerceIn(2.0.pow(1.0), 2.0.pow(19.0))

        // New zoom as float (log base 2 of scale)
        val newZoomFloat = ln(newMapScale) / ln(2.0)
        val newZoomInt = newZoomFloat.toInt()

        // Pixel coords of center at old integer zoom
        val centerPixelXOldZoom = OsmGeometryUtil.lon2num(centerLon, oldZoomInt) * tileSize
        val centerPixelYOldZoom = OsmGeometryUtil.lat2num(centerLat, oldZoomInt) * tileSize

        // Position of focus point on the map in pixels at old integer zoom
        val focusPixelXOldZoom = centerPixelXOldZoom + (lastScaleFocusX - width / 2.0)
        val focusPixelYOldZoom = centerPixelYOldZoom + (lastScaleFocusY - height / 2.0)

        // Lat/Lon of focus point before zoom (based on old zoom integer level)
        val focusLon = OsmGeometryUtil.numToLon(focusPixelXOldZoom / tileSize, oldZoomInt)
        val focusLat = OsmGeometryUtil.numToLat(focusPixelYOldZoom / tileSize, oldZoomInt)

        // Pixel coords of focus point at new integer zoom
        val focusPixelXNewZoom = OsmGeometryUtil.lon2num(focusLon, newZoomInt) * tileSize
        val focusPixelYNewZoom = OsmGeometryUtil.lat2num(focusLat, newZoomInt) * tileSize

        // Adjust center pixel so focus point stays under finger
        val newCenterPixelX = focusPixelXNewZoom - (lastScaleFocusX - width / 2.0)
        val newCenterPixelY = focusPixelYNewZoom - (lastScaleFocusY - height / 2.0)

        // Convert new center pixel coords back to lat/lon at new integer zoom
        centerLon = OsmGeometryUtil.numToLon(newCenterPixelX / tileSize, newZoomInt)
        centerLat = OsmGeometryUtil.numToLat(newCenterPixelY / tileSize, newZoomInt)

        // Update zoom level as float (fractional zoom)
        zoomLevel = newZoomFloat.toFloat()

        // Reset visual scaling and offsets
        visualZoomScale = 1f
        offsetX = 0f
        offsetY = 0f
    }


    private fun panMap(dx: Float, dy: Float) {
        offsetX -= dx
        offsetY -= dy
        invalidate()
        needsRedraw = true
    }

    private fun commitPan() {
        val tileSize = 256.0
        val zoom = zoomLevel.toInt()

        val deltaTileX = -offsetX / tileSize
        val deltaTileY = -offsetY / tileSize

        val currentTileX = OsmGeometryUtil.lon2num(centerLon, zoom)
        val currentTileY = OsmGeometryUtil.lat2num(centerLat, zoom)

        val newTileX = currentTileX + deltaTileX
        val newTileY = currentTileY + deltaTileY

        centerLon = OsmGeometryUtil.numToLon(newTileX, zoom)
        centerLat = OsmGeometryUtil.numToLat(newTileY, zoom)

        offsetX = 0f
        offsetY = 0f
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
