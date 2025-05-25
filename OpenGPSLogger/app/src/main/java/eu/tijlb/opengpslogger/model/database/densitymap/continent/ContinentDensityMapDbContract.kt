package eu.tijlb.opengpslogger.model.database.densitymap.continent

import android.provider.BaseColumns

object ContinentDensityMapDbContract : BaseColumns {
    const val TABLE_NAME = "density_map_continent"
    const val COLUMN_NAME_X_INDEX = "idx_x"
    const val COLUMN_NAME_Y_INDEX = "idx_y"
    const val COLUMN_NAME_AMOUNT = "amount"
    const val COLUMN_NAME_LAST_POINT_TIME = "last_point_time"

}