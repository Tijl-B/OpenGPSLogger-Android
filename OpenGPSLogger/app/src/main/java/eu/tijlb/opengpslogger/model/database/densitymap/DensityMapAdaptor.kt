package eu.tijlb.opengpslogger.model.database.densitymap;

import android.content.Context;
import android.database.Cursor
import android.util.Log
import eu.tijlb.opengpslogger.model.database.densitymap.impl.CityDensityMapDbHelper
import eu.tijlb.opengpslogger.model.database.densitymap.impl.ContinentDensityMapDbHelper
import eu.tijlb.opengpslogger.model.database.densitymap.impl.CountryDensityMapDbHelper
import eu.tijlb.opengpslogger.model.database.densitymap.impl.StreetDensityMapDbHelper
import eu.tijlb.opengpslogger.model.database.densitymap.impl.WorldDensityMapDbHelper
import eu.tijlb.opengpslogger.model.dto.BBoxDto

class DensityMapAdaptor(context: Context) {

    private val worldDensityMapDbHelper: WorldDensityMapDbHelper =
        WorldDensityMapDbHelper.getInstance(context)

    private val continentDensityMapDbHelper: ContinentDensityMapDbHelper =
        ContinentDensityMapDbHelper.getInstance(context)

    private val countryDensityMapDbHelper: CountryDensityMapDbHelper =
        CountryDensityMapDbHelper.getInstance(context)

    private val cityDensityMapDbHelper: CityDensityMapDbHelper =
        CityDensityMapDbHelper.getInstance(context)

    private val streetDensityMapDbHelper: StreetDensityMapDbHelper =
        StreetDensityMapDbHelper.getInstance(context)

    fun getPoints(bbox: BBoxDto, zoomLevel: Int): Cursor {
        return getDbHelper(zoomLevel).getPoints(bbox)
    }

    fun getSubdivisions(zoomLevel: Int): Int {
        return getDbHelper(zoomLevel).subdivisions()
    }

    fun drop() {
        getAllDbHelpers().forEach { dbHelper ->
            dbHelper.drop()
        }
    }

    fun addPoint(latitude: Double, longitude: Double, time: Long) {
        getAllDbHelpers().forEach { dbHelper ->
            dbHelper.addPoint(latitude, longitude, time)
        }
    }

    private fun getDbHelper(zoomLevel: Int): AbstractDensityMapDbHelper {
        val helper = when {
            zoomLevel >= 17 -> streetDensityMapDbHelper
            zoomLevel >= 13 -> cityDensityMapDbHelper
            zoomLevel >= 9 -> countryDensityMapDbHelper
            zoomLevel >= 6 -> continentDensityMapDbHelper
            else -> worldDensityMapDbHelper
        }
        Log.d("ogl-densitymapadaptor", "Using density map helper $helper for zoom $zoomLevel")
        return helper
    }

    private fun getAllDbHelpers(): List<AbstractDensityMapDbHelper> {
        return listOf(
            worldDensityMapDbHelper,
            continentDensityMapDbHelper,
            countryDensityMapDbHelper,
            cityDensityMapDbHelper,
            streetDensityMapDbHelper
        )
    }

    companion object {
        private var instance: DensityMapAdaptor? = null
        fun getInstance(context: Context): DensityMapAdaptor {
            return instance ?: synchronized(this) {
                instance ?: DensityMapAdaptor(context).also { instance = it }
            }
        }
    }
}
