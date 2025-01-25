package eu.tijlb.opengpslogger.query

import eu.tijlb.opengpslogger.dto.BBoxDto

data class PointsQuery(
    val dataSource: String,
    val startDateMillis: Long,
    val endDateMillis: Long,
    var bbox: BBoxDto? = null,
    var minAccuracy: Float? = null
)
