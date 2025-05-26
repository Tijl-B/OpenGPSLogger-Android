package eu.tijlb.opengpslogger.model.util

import android.util.Log
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

object OsmGeometryUtil {

    fun lat2num(lat: Double, zoom: Int): Double {
        val n = 2.0.pow(zoom)
        val latRad = Math.toRadians(lat)
        return n * (1.0 - (ln(tan(latRad) + 1 / cos(latRad)) / Math.PI)) / 2.0
    }

    fun lon2num(lon: Double, zoom: Int): Double {
        val n = 2.0.pow(zoom)
        return n * (lon + 180.0) / 360.0
    }

    fun numToLon(x: Double, zoom: Int): Double {
        val n = 2.0.pow(zoom)
        return x / n * 360.0 - 180.0
    }

    fun numToLat(y: Double, zoom: Int): Double {
        val n = 2.0.pow(zoom)
        val latRad = atan(sinh(Math.PI * (1 - 2 * y / n)))
        return Math.toDegrees(latRad)
    }

    fun deg2num(lat: Double, lon: Double, zoom: Int): Pair<Double, Double> {
        val xtile = lon2num(lon, zoom)
        val ytile = lat2num(lat, zoom)
        return Pair(xtile, ytile)
    }

    fun calculateZoomLevel(bbox: BBoxDto): Int {
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
}
