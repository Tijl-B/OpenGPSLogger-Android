package eu.tijlb.opengpslogger.util

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6371000

class OutlierUtil {
    data class Point(val id: Long, val latitude: Double, val longitude: Double, val timestamp: Long)

    companion object {
        fun calculateAngle(prev: Point, middle: Point, next: Point): Double? {
            val vector1 = Pair(middle.latitude - prev.latitude, middle.longitude - prev.longitude)
            val vector2 = Pair(next.latitude - middle.latitude, next.longitude - middle.longitude)

            val dotProduct = vector1.first * vector2.first + vector1.second * vector2.second
            val magnitude1 = sqrt(vector1.first * vector1.first + vector1.second * vector1.second)
            val magnitude2 = sqrt(vector2.first * vector2.first + vector2.second * vector2.second)

            return Math.toDegrees(acos(dotProduct / (magnitude1 * magnitude2)))
                .takeUnless { it.isNaN() }
        }

        fun calculateDistance(prev: Point, middle: Point, next: Point): Double {
            return calculateDistance(prev, middle) + calculateDistance(middle, next)
        }

        fun calculateDistance(point1: Point, point2: Point): Double {
            val dLat = Math.toRadians(point2.latitude - point1.latitude)
            val dLon = Math.toRadians(point2.longitude - point1.longitude)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(point1.latitude)) * cos(Math.toRadians(point2.latitude)) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_METERS * c
        }
    }
}