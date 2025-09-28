package eu.tijlb.opengpslogger.model.exporter

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Xml
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.dto.query.PointsQuery
import kotlinx.coroutines.flow.collectIndexed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


private const val TAG = "ogl-gpxexporter"

class GpxExporter(val context: Context) {
    private val locationDbHelper: LocationDbHelper =
        LocationDbHelper.getInstance(context.applicationContext)

    suspend fun export(query: PointsQuery): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val fileName = "ogl-export-$timestamp.gpx"

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/gpx+xml")
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("Failed to create file in Downloads")

        resolver.openOutputStream(uri)?.use { fos ->
            val serializer = Xml.newSerializer()
            serializer.setOutput(fos, "UTF-8")
            serializer.startDocument("UTF-8", true)
            serializer.startTag("", "gpx")
            serializer.attribute("", "version", "0")
            serializer.attribute("", "creator", "ogl")

            serializer.startTag("", "trk")
            serializer.startTag("", "name")
            serializer.text("Exported Track")
            serializer.endTag("", "name")
            serializer.startTag("", "trkseg")

            locationDbHelper.getPointsFlow(query).collectIndexed { _, point ->
                serializer.startTag("", "trkpt")
                serializer.attribute("", "lat", point.latitude.toString())
                serializer.attribute("", "lon", point.longitude.toString())

                serializer.startTag("", "time")
                serializer.text(
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                        .format(Date(point.timestamp))
                )
                serializer.endTag("", "time")

                serializer.endTag("", "trkpt")
            }

            serializer.endTag("", "trkseg")
            serializer.endTag("", "trk")

            serializer.endTag("", "gpx")
            serializer.endDocument()
            serializer.flush()
        }
        shareGpxFile(uri)
        return fileName
    }

    fun shareGpxFile(uri: Uri) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share GPX file")

        if (context !is android.app.Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooser)
    }

}