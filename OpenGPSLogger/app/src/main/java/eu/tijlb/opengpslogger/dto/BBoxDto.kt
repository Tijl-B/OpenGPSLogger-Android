package eu.tijlb.opengpslogger.dto

import kotlin.math.max
import kotlin.math.min

private const val MAX_LAT = 85.0
private const val MIN_LAT = -MAX_LAT

private const val MAX_LON = 179.99
private const val MIN_LON = -MAX_LON

data class BBoxDto(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
) {
    fun latRange(): Double {
        return maxLat - minLat
    }

    fun lonRange(): Double {
        return maxLon - minLon
    }

    fun expand(expansionFactor: Double): BBoxDto {
        val latRange = latRange()
        val lonRange = lonRange()

        val latExpansion = expansionFactor * latRange
        val lonExpansion = expansionFactor * lonRange

        return BBoxDto(
            minLat = max(minLat - latExpansion, MIN_LAT),
            maxLat = min(maxLat + latExpansion, MAX_LAT),
            minLon = max(minLon - lonExpansion, MIN_LON),
            maxLon = min(maxLon + lonExpansion, MAX_LON)
        )
    }
    companion object {
        fun defaultBbox(): BBoxDto {
            return BBoxDto(MIN_LAT, MAX_LAT, MIN_LON, MAX_LON)
        }
    }
}
