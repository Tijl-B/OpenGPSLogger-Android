package eu.tijlb.opengpslogger.model.exporter

import android.content.Context
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.dto.query.PointsQuery


private const val TAG = "ogl-gpxexporter"

class GpxExporter(context: Context) {
    private val locationDbHelper: LocationDbHelper =
        LocationDbHelper.getInstance(context.applicationContext)

    suspend fun export(query: PointsQuery) {
    }

}