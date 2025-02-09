package eu.tijlb.opengpslogger.model.database.boundingbox

import android.provider.BaseColumns

object BoundingBoxDbContract : BaseColumns {
    const val TABLE_NAME = "bounding_box"
    const val COLUMN_NAME_NAME = "name"
    const val COLUMN_NAME_MAX_LATITUDE = "max_latitude"
    const val COLUMN_NAME_MIN_LATITUDE = "min_latitude"
    const val COLUMN_NAME_MAX_LONGITUDE = "max_longitude"
    const val COLUMN_NAME_MIN_LONGITUDE = "min_longitude"
    const val COLUMN_NAME_CREATED_ON = "created_on"

}