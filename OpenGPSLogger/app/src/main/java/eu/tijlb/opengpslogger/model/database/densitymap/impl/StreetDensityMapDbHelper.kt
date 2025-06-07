package eu.tijlb.opengpslogger.model.database.densitymap.impl

import android.content.Context
import eu.tijlb.opengpslogger.model.database.densitymap.AbstractDensityMapDbHelper

class StreetDensityMapDbHelper(context: Context) :
    AbstractDensityMapDbHelper(context, DATABASE_NAME, DATABASE_VERSION) {

    override fun subdivisions(): Int {
        return 5_000_000
    }

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "densitymap_street.sqlite"

        private var instance: StreetDensityMapDbHelper? = null
        fun getInstance(context: Context): StreetDensityMapDbHelper {
            return instance ?: synchronized(this) {
                instance ?: StreetDensityMapDbHelper(context).also { instance = it }
            }
        }
    }
}
