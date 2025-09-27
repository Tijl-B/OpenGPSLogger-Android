package eu.tijlb.opengpslogger.model.database.densitymap;

import android.content.Context;
import android.database.Cursor
import android.location.Location
import android.util.Log
import eu.tijlb.opengpslogger.model.database.densitymap.impl.CityDensityMapDbHelper
import eu.tijlb.opengpslogger.model.database.densitymap.impl.ContinentDensityMapDbHelper
import eu.tijlb.opengpslogger.model.database.densitymap.impl.CountryDensityMapDbHelper
import eu.tijlb.opengpslogger.model.database.densitymap.impl.StreetDensityMapDbHelper
import eu.tijlb.opengpslogger.model.database.densitymap.impl.WorldDensityMapDbHelper
import eu.tijlb.opengpslogger.model.dto.BBoxDto

class DensityMapAdapter(context: Context) {

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
        getAllDbHelpers().forEach { dbHelper -> dbHelper.drop() }
    }

    fun addPoint(latitude: Double, longitude: Double, time: Long) {
        getAllDbHelpers().forEach { dbHelper -> dbHelper.addPoint(latitude, longitude, time) }
    }

    fun deletePoint(latitude: Double, longitude: Double) {
        getAllDbHelpers().forEach { dbHelper -> dbHelper.deletePoint(latitude, longitude) }
    }

    fun addLocation(location: Location) {
        Log.d("ogl-densitymapadapter", "Adding location $location to all density map dbs")
        getAllDbHelpers().forEach { dbHelper -> dbHelper.addLocation(location) }
    }

    private fun getDbHelper(zoomLevel: Int): AbstractDensityMapDbHelper {
        val helper = when {
            zoomLevel >= 13 -> streetDensityMapDbHelper
            zoomLevel >= 10 -> cityDensityMapDbHelper
            zoomLevel >= 7 -> countryDensityMapDbHelper
            zoomLevel >= 4 -> continentDensityMapDbHelper
            else -> worldDensityMapDbHelper
        }
        Log.d("ogl-densitymapadapter", "Using density map helper $helper for zoom $zoomLevel")
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
        private var instance: DensityMapAdapter? = null
        fun getInstance(context: Context): DensityMapAdapter {
            return instance ?: synchronized(this) {
                instance ?: DensityMapAdapter(context.applicationContext).also { instance = it }
            }
        }
    }
}
