package eu.tijlb.opengpslogger.model.database.locationbuffer

import android.provider.BaseColumns

object LocationBufferDbContract : BaseColumns {
        const val FILE_NAME = "locationbuffer.sqlite"
        const val TABLE_NAME = "location_buffer"

        const val COLUMN_NAME_TIMESTAMP = "timestamp"
        const val COLUMN_NAME_LATITUDE = "latitude"
        const val COLUMN_NAME_LONGITUDE = "longitude"
        const val COLUMN_NAME_SPEED = "speed"
        const val COLUMN_NAME_SPEED_ACCURACY = "speed_accuracy"
        const val COLUMN_NAME_ACCURACY = "accuracy"
        const val COLUMN_NAME_SOURCE = "source"
}