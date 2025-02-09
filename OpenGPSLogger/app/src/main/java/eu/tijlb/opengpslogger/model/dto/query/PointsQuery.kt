package eu.tijlb.opengpslogger.model.dto.query

import eu.tijlb.opengpslogger.model.dto.BBoxDto

data class PointsQuery(
    val dataSource: String,
    val startDateMillis: Long,
    val endDateMillis: Long,
    var bbox: BBoxDto? = null,
    var minAccuracy: Float? = null,
    val minAngle: Float
)
