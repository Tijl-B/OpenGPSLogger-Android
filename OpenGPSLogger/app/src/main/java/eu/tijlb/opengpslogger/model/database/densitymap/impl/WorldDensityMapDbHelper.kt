package eu.tijlb.opengpslogger.model.database.densitymap.impl

import android.content.Context
import eu.tijlb.opengpslogger.model.database.densitymap.AbstractDensityMapDbHelper

class WorldDensityMapDbHelper(context: Context) :
    AbstractDensityMapDbHelper(context, DATABASE_NAME, DATABASE_VERSION) {

    override fun subdivisions(): Int {
        return 3_000
    }

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "densitymap_world.sqlite"

        private var instance: WorldDensityMapDbHelper? = null
        fun getInstance(context: Context): WorldDensityMapDbHelper {
            return instance ?: synchronized(this) {
                instance ?: WorldDensityMapDbHelper(context).also { instance = it }
            }
        }
    }
}
