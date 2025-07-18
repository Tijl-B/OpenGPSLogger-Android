package eu.tijlb.opengpslogger.ui.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.OnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import eu.tijlb.opengpslogger.model.database.settings.BrowseSettingsHelper
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.model.util.OsmGeometryUtil
import eu.tijlb.opengpslogger.ui.util.LockUtil.runIfLast
import eu.tijlb.opengpslogger.ui.util.LockUtil.tryLockOrSkip
import eu.tijlb.opengpslogger.ui.view.bitmap.CopyRightNoticeBitmapRenderer
import eu.tijlb.opengpslogger.ui.view.bitmap.DensityMapBitmapRenderer
import eu.tijlb.opengpslogger.ui.view.bitmap.OsmImageBitmapRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.log2
import kotlin.math.pow

private const val MIN_ZOOM = 4.0
private const val MAX_ZOOM = 20.0

private const val TAG = "ogl-layeredmapview"

class LayeredMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs), OnGestureListener,
    ScaleGestureDetector.OnScaleGestureListener {

    private val gestureDetector = GestureDetector(context, this)
    private val scaleGestureDetector = ScaleGestureDetector(context, this)
    private val browseSettingsHelper = BrowseSettingsHelper(context)

    private val layers = listOf(
        MapLayer(OsmImageBitmapRenderer(context)),
        MapLayer(DensityMapBitmapRenderer(context)),
        MapLayer(CopyRightNoticeBitmapRenderer(context))
    )

    private var centerLat = 0.0
    private var centerLon = 0.0

    private var visualZoomLevel = 4.0

    private var needsRedraw = true
    private val redrawMutex = Mutex()
    private val latestRedrawJob = AtomicReference<Job?>(null)
    private val setupMutex = Mutex()
    private var setupJob: Job? = null
    private var redrawJob: Job? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Log.d(TAG, "onDraw")
        if (setupJob == null) {
            setupJob = CoroutineScope(Dispatchers.IO).launch {
                setupMutex.tryLockOrSkip {
                    setUpCenterAndZoom()
                    redrawIfNeeded()
                    invalidate()
                }
            }
        } else {
            layers.forEach { it.drawBitmapOnCanvas(canvas, visualZoomLevel) }
        }
    }

    override fun onDetachedFromWindow() {
        Log.d(TAG, "Detaching LayeredMapView from window...")
        cancelJobs()
        super.onDetachedFromWindow()
    }

    public fun cancelJobs() {
        layers.forEach { it.cancelJob() }
        setupJob?.cancel()
        redrawJob?.cancel()
    }

    private fun setUpCenterAndZoom() {
        val centerCoords = browseSettingsHelper.getCenterCoords()
        centerLat = centerCoords.first
        centerLon = centerCoords.second
        visualZoomLevel = browseSettingsHelper.getZoom().toDouble()
    }

    private fun redrawIfNeeded() {
        val oldJob = redrawJob
        redrawJob = CoroutineScope(Dispatchers.IO).launch {
            redrawMutex.runIfLast(latestRedrawJob) {
                if (needsRedraw) {
                    Log.d(TAG, "Starting redraw")
                    oldJob?.cancel()
                    needsRedraw = false
                    layers.forEach { it.cancelJob() }
                    browseSettingsHelper.setCenterCoords(centerLat, centerLon)
                    browseSettingsHelper.setZoom(visualZoomLevel.toFloat())
                    commitPan()
                    commitZoom()
                    invalidate()
                    loadLayers()
                        .forEach { job -> job.join() }
                    Log.d(TAG, "Done with redraw")
                }
            }
        }
    }

    private fun loadLayers(): List<Job> {
        Log.d(TAG, "Loading tiles $centerLat, $centerLon, $width, $height")
        val newBitmapZoom = visualZoomLevel.toInt()
        val bbox = bboxFromCenter(centerLat, centerLon, newBitmapZoom, width, height)
        return layers.map {
            it.startDrawJob(bbox, Pair(width, height)) { invalidate() }
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

        val anyZoomOutOfSync = layers.map { it.requiresUpdate(visualZoomLevel) }
            .contains(true)
        if (anyZoomOutOfSync) {
            needsRedraw = true
            redrawIfNeeded()
        }
        invalidate()
    }

    private fun commitZoom() {
        val newZoom = visualZoomLevel.toInt()
        Log.d(TAG, "Commit visualZoomScale to $newZoom")
        layers.forEach { it.commitZoom(newZoom) }
    }

    private fun calculateNewZoom(scale: Float): Double {
        val oldMapScale = 2.0.pow(visualZoomLevel)
        val newMapScale = (oldMapScale * scale)
        val newZoom = log2(newMapScale)
        return newZoom
    }

    private fun panMap(dx: Float, dy: Float) {
        layers.forEach { it.visualPan(dx, dy) }
        needsRedraw = true
        //redrawIfNeeded()
        invalidate()
    }

    private fun commitPan() {
        layers.map { it.commitPan(visualZoomLevel) }
            .first()
            ?.let { (deltaX, deltaY, zoom) ->
                val tileSize = 256.0
                val scale = 2.0.pow(zoom)
                val mapSize = tileSize * scale

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


                Log.d(TAG, "Center before pan: $centerLon, $centerLat")
                centerLon = OsmGeometryUtil.numToLon(newCenterPixelX / tileSize, zoom)
                centerLat = OsmGeometryUtil.numToLat(newCenterPixelY / tileSize, zoom)
                Log.d(TAG, "Center after pan: $centerLon, $centerLat")
            }
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
        Log.d(TAG, "scrolling x $distanceX y $distanceY (${e1?.action}, ${e2.action})")
        panMap(distanceX, distanceY)
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        Log.d(TAG, "scaling with factor ${detector.scaleFactor}")
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
