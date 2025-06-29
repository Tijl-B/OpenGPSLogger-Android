package eu.tijlb.opengpslogger.model.database.densitymap.impl

import android.content.Context
import eu.tijlb.opengpslogger.model.database.densitymap.AbstractDensityMapDbHelper

class CountryDensityMapDbHelper(context: Context) :
    AbstractDensityMapDbHelper(context, DATABASE_NAME, DATABASE_VERSION) {

    override fun subdivisions(): Int {
        return 100_000
    }

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "densitymap_country.sqlite"

        private var instance: CountryDensityMapDbHelper? = null
        fun getInstance(context: Context): CountryDensityMapDbHelper {
            return instance ?: synchronized(this) {
                instance ?: CountryDensityMapDbHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
