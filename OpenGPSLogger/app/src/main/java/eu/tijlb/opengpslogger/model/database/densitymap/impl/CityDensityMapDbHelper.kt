package eu.tijlb.opengpslogger.model.database.densitymap.impl

import android.content.Context
import eu.tijlb.opengpslogger.model.database.densitymap.AbstractDensityMapDbHelper

class CityDensityMapDbHelper(context: Context) :
    AbstractDensityMapDbHelper(context, DATABASE_NAME, DATABASE_VERSION) {

    override fun subdivisions(): Int {
        return 750_000
    }

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "densitymap_continent.sqlite"

        private var instance: CityDensityMapDbHelper? = null
        fun getInstance(context: Context): CityDensityMapDbHelper {
            return instance ?: synchronized(this) {
                instance ?: CityDensityMapDbHelper(context).also { instance = it }
            }
        }
    }
}
