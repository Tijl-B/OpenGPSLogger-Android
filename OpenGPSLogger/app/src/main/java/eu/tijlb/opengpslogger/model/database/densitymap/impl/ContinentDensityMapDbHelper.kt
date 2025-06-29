package eu.tijlb.opengpslogger.model.database.densitymap.impl

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import eu.tijlb.opengpslogger.model.database.densitymap.AbstractDensityMapDbHelper

class ContinentDensityMapDbHelper(context: Context) :
    AbstractDensityMapDbHelper(context, DATABASE_NAME, DATABASE_VERSION) {

    override fun subdivisions(): Int {
        return 10_000
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            onCreate(db)
        }
    }

    companion object {
        const val DATABASE_VERSION = 2
        const val DATABASE_NAME = "densitymap_continent.sqlite"

        private var instance: ContinentDensityMapDbHelper? = null
        fun getInstance(context: Context): ContinentDensityMapDbHelper {
            return instance ?: synchronized(this) {
                instance ?: ContinentDensityMapDbHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
