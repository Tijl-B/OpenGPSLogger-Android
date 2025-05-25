package eu.tijlb.opengpslogger.model.dto.query

import eu.tijlb.opengpslogger.model.dto.BBoxDto

const val DATASOURCE_ALL = "All"

data class PointsQuery(
    val dataSource: String = DATASOURCE_ALL,
    val startDateMillis: Long = 0,
    val endDateMillis: Long = Long.MAX_VALUE,
    var bbox: BBoxDto? = null,
    var minAccuracy: Float? = null,
    val minAngle: Float = 0F
)
