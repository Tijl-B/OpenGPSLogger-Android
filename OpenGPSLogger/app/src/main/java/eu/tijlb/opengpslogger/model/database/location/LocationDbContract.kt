package eu.tijlb.opengpslogger.model.database.location

import android.provider.BaseColumns

object LocationDbContract : BaseColumns {
        const val FILE_NAME = "location.sqlite"
        const val TABLE_NAME = "location"

        const val COLUMN_NAME_TIMESTAMP = "timestamp"
        const val COLUMN_NAME_LATITUDE = "latitude"
        const val COLUMN_NAME_LONGITUDE = "longitude"
        const val COLUMN_NAME_SPEED = "speed"
        const val COLUMN_NAME_SPEED_ACCURACY = "speed_accuracy"
        const val COLUMN_NAME_ACCURACY = "accuracy"
        const val COLUMN_NAME_SOURCE = "source"
        const val COLUMN_NAME_CREATED_ON = "created_on"
        const val COLUMN_NAME_HASH_50M_1D = "hash_50m_1d"
        const val COLUMN_NAME_HASH_250M_1D = "hash_250m_1d"
        const val COLUMN_NAME_NEIGHBOR_DISTANCE = "neighbor_distance"
        const val COLUMN_NAME_NEIGHBOR_ANGLE = "neighbor_angle"
}

object LocationDbMigrationContract : BaseColumns {
        const val TABLE_NAME = "migration"

        const val COLUMN_NAME_VERSION = "version"
        const val COLUMN_NAME_EXECUTED_ON = "executed_on"
}