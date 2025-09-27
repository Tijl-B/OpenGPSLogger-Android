package eu.tijlb.opengpslogger.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import eu.tijlb.opengpslogger.model.database.centercoords.BrowseSettingsHelper
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.model.util.OsmGeometryUtil
import eu.tijlb.opengpslogger.ui.util.LockUtil.runIfLast
import eu.tijlb.opengpslogger.ui.util.LockUtil.tryLockOrSkip
import eu.tijlb.opengpslogger.ui.view.bitmap.CopyRightNoticeBitmapRenderer
import eu.tijlb.opengpslogger.ui.view.bitmap.DensityMapBitmapRenderer
import eu.tijlb.opengpslogger.ui.view.bitmap.LastLocationBitmapRenderer
import eu.tijlb.opengpslogger.ui.view.bitmap.OsmImageBitmapRenderer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.log2
import kotlin.math.pow

private const val MIN_ZOOM = 4.0
private const val MAX_ZOOM = 20.0
private const val TAG = "ogl-layeredmapview"

class LayeredMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs), GestureDetector.OnGestureListener,
    ScaleGestureDetector.OnScaleGestureListener {

    private val gestureDetector = GestureDetector(context, this)
    private val scaleGestureDetector = ScaleGestureDetector(context, this)
    private val browseSettingsHelper = BrowseSettingsHelper(context.applicationContext)

    private val layers = listOf(
        MapLayer(OsmImageBitmapRenderer(context)),
        MapLayer(DensityMapBitmapRenderer(context)),
        MapLayer(CopyRightNoticeBitmapRenderer(context)),
        MapLayer(LastLocationBitmapRenderer(context))
    )

    private var centerLat = 0.0
    private var centerLon = 0.0
    private var visualZoomLevel = 4.0

    private val needsRedraw = AtomicBoolean(true)
    private val redrawMutex = Mutex()
    private val latestRedrawJob = AtomicReference<Job?>(null)
    private val setupMutex = Mutex()
    private var setupJob: Job? = null
    private var redrawJob: Job? = null

    private val scope = MainScope()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Log.d(TAG, "onDraw")

        if (setupJob == null) {
            setupJob = scope.launch(Dispatchers.IO) {
                setupMutex.tryLockOrSkip {
                    setUpCenterAndZoom()
                    redrawIfNeeded()
                    postInvalidate()
                }
            }
        } else {
            layers.forEach { it.drawBitmapOnCanvas(canvas, visualZoomLevel) }
            redrawIfNeeded()
        }
    }

    override fun onDetachedFromWindow() {
        Log.d(TAG, "Detaching LayeredMapView from window...")
        stopUpdates()
        scope.cancel()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        Log.d(TAG, "Attaching LayeredMapView to window...")
        super.onAttachedToWindow()
        redraw()
    }

    fun stopUpdates() {
        layers.forEach { it.cancelJob() }
        setupJob?.cancel()
        redrawJob?.cancel()
    }

    fun redraw() {
        needsRedraw.set(true)
        redrawIfNeeded()
    }

    fun boundingBox(): BBoxDto {
        return bboxFromCenter(centerLat, centerLon, visualZoomLevel, width, height)
    }

    private fun setUpCenterAndZoom() {
        val centerCoords = browseSettingsHelper.getCenterCoords()
        centerLat = centerCoords.first
        centerLon = centerCoords.second
        visualZoomLevel = browseSettingsHelper.getZoom().toDouble()
        Log.i(TAG, "Set up center $centerLat, $centerLon and zoom $visualZoomLevel")
    }

    private fun redrawIfNeeded() {
        if (!needsRedraw.get()) return
        if (!(setupJob?.isCompleted ?: false)) {
            Log.d(TAG, "Not yet set up, skipping redraw")
            return
        }

        val oldJob = redrawJob
        redrawJob = scope.launch(Dispatchers.IO) {
            val uuid = UUID.randomUUID()
            Log.d(TAG, "Canceling old redraw job $uuid")
            oldJob?.cancelAndJoin()
            Log.d(TAG, "Getting redraw mutex $uuid")
            redrawMutex.runIfLast(latestRedrawJob) {
                Log.d(TAG, "Got redraw mutex $uuid")
                if (needsRedraw.getAndSet(false)) {
                    Log.d(TAG, "Starting redraw $uuid")
                    layers.forEach { it.cancelAndJoin() }
                    browseSettingsHelper.setCenterCoords(centerLat, centerLon)
                    browseSettingsHelper.setZoom(visualZoomLevel.toFloat())
                    commitPanAndZoom()
                    loadLayers().forEach { it.join() }
                    Log.d(TAG, "Done with redraw $uuid")
                    postInvalidate()
                }
            }
            Log.d(TAG, "End of redraw job $uuid")
        }
    }

    private suspend fun loadLayers(): List<Job> {
        Log.d(TAG, "Loading tiles $centerLat, $centerLon, $width, $height")
        val bbox = bboxFromCenter(centerLat, centerLon, visualZoomLevel, width, height)
        return layers.map {
            it.startDrawJob(scope, bbox, visualZoomLevel - 1, width to height) {
                postInvalidate()
            }
        }
    }

    private fun bboxFromCenter(
        lat: Double, lon: Double, zoom: Double, viewWidth: Int, viewHeight: Int
    ): BBoxDto {
        val tileSize = 256.0
        val centerX = OsmGeometryUtil.lon2num(lon, zoom) * tileSize
        val centerY = OsmGeometryUtil.lat2num(lat, zoom) * tileSize
        val halfWidth = viewWidth / 2.0
        val halfHeight = viewHeight / 2.0
        val minPxX = centerX - halfWidth
        val maxPxX = centerX + halfWidth
        val minPxY = centerY - halfHeight
        val maxPxY = centerY + halfHeight
        val minLon = OsmGeometryUtil.numToLon(minPxX / tileSize, zoom)
        val maxLon = OsmGeometryUtil.numToLon(maxPxX / tileSize, zoom)
        val minLat = OsmGeometryUtil.numToLat(maxPxY / tileSize, zoom)
        val maxLat = OsmGeometryUtil.numToLat(minPxY / tileSize, zoom)
        return BBoxDto(minLat, maxLat, minLon, maxLon).coerce()
    }

    private fun calculateNewZoom(scale: Float): Double {
        val oldMapScale = 2.0.pow(visualZoomLevel)
        val newMapScale = oldMapScale * scale
        return log2(newMapScale)
    }

    private fun commitPanAndZoom() {
        val oldZoom = layers.first().zoom
        val newZoom = visualZoomLevel
        Log.d(TAG, "Commit visualZoomScale to $newZoom")
        layers.map { it.commitPanAndZoom() }
            .first()
            ?.let { updateCenterCoordsFromMatrix(oldZoom, newZoom, it) }
    }

    private fun updateCenterCoordsFromMatrix(oldZoom: Double, newZoom: Double, matrix: Matrix) {
        val tileSize = 256.0
        val oldScale = 2.0.pow(oldZoom)
        val newScale = 2.0.pow(newZoom)
        val ratio = newScale / oldScale
        val mapSize = tileSize * newScale

        val centerPixelX = OsmGeometryUtil.lon2num(centerLon, oldZoom) * tileSize
        val centerPixelY = OsmGeometryUtil.lat2num(centerLat, oldZoom) * tileSize
        val halfWidth = width / 2.0
        val halfHeight = height / 2.0

        val src = floatArrayOf(halfWidth.toFloat(), halfHeight.toFloat())
        val dst = FloatArray(2)
        matrix.mapPoints(dst, src)
        var newCenterPixelX = centerPixelX * ratio - (dst[0] - halfWidth)
        var newCenterPixelY = centerPixelY * ratio - (dst[1] - halfHeight)

        val minCenterX = halfWidth
        val maxCenterX = mapSize - halfWidth
        val minCenterY = halfHeight
        val maxCenterY = mapSize - halfHeight

        if (minCenterX >= maxCenterX || minCenterY >= maxCenterY) {
            Log.e(
                TAG,
                "Invalid center coord ranges: x[$minCenterX, $maxCenterX], y[$minCenterY, $maxCenterY]"
            )
            return
        }

        newCenterPixelX = newCenterPixelX.coerceIn(minCenterX, maxCenterX)
        newCenterPixelY = newCenterPixelY.coerceIn(minCenterY, maxCenterY)

        Log.d(TAG, "Center before pan: $centerLon, $centerLat; zoom: $oldZoom")
        centerLon = OsmGeometryUtil.numToLon(newCenterPixelX / tileSize, newZoom)
        centerLat = OsmGeometryUtil.numToLat(newCenterPixelY / tileSize, newZoom)
        Log.d(TAG, "Center after pan: $centerLon, $centerLat; zoom: $newZoom")
    }

    // -- Gesture Handling --

    override fun onDown(e: MotionEvent) = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        scaleGestureDetector.onTouchEvent(event)
        needsRedraw.set(true)
        invalidate()
        return true
    }

    override fun onScroll(
        e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float
    ): Boolean {
        Log.d(TAG, "scrolling x $distanceX y $distanceY (${e1?.action}, ${e2.action})")
        layers.forEach { it.onScroll(distanceX, distanceY) }
        needsRedraw.set(true)
        invalidate()
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        Log.d(TAG, "scaling with factor ${detector.scaleFactor}")
        visualZoomLevel = calculateNewZoom(detector.scaleFactor)
            .coerceIn(MIN_ZOOM, MAX_ZOOM)
        layers.forEach { it.onScale(detector) }
        needsRedraw.set(true)
        invalidate()
        return true
    }

    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent) = false
    override fun onLongPress(e: MotionEvent) {}
    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float) =
        false

    override fun onScaleBegin(detector: ScaleGestureDetector) = true
    override fun onScaleEnd(detector: ScaleGestureDetector) {}
}
