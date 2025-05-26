package eu.tijlb.opengpslogger.model.parser.gpx

import android.util.Log
import eu.tijlb.opengpslogger.model.util.TimeUtil
import eu.tijlb.opengpslogger.ui.activity.ImportActivity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

object GpxParser {

    private const val GPX_FIELD_TRACKPOINT = "trkpt"
    private const val GPX_FIELD_TIME = "time"
    private const val GPX_ATTR_LAT = "lat"
    private const val GPX_ATTR_LON = "lon"
    private const val GPX_FIELD_SPEED = "speed"

    fun parse(
        inputStream: InputStream,
        save: (ImportActivity.Point, importStart: Long, source: String) -> Unit
    ) {
        try {
            val importStart = System.currentTimeMillis()

            val parserFactory = XmlPullParserFactory.newInstance()
            val parser = parserFactory.newPullParser()
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            var latitude: String? = null
            var longitude: String? = null
            var time: Long? = null
            var speed: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tagName) {
                            GPX_FIELD_TRACKPOINT -> {
                                latitude = parser.getAttributeValue(null, GPX_ATTR_LAT)
                                longitude = parser.getAttributeValue(null, GPX_ATTR_LON)
                            }

                            GPX_FIELD_TIME -> time =
                                TimeUtil.iso8601ToUnixMillis(parser.nextText())

                            GPX_FIELD_SPEED -> speed = parser.nextText()
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (tagName == GPX_FIELD_TRACKPOINT && latitude != null && longitude != null) {
                            val point = ImportActivity.Point(
                                lat = latitude.toDouble(),
                                lon = longitude.toDouble(),
                                unixTime = time,
                                gpsSpeed = speed?.toFloat()
                            )
                            save(
                                point,
                                importStart,
                                "gpx_import"
                            )
                            latitude = null
                            longitude = null
                        }
                    }
                }
                eventType = parser.next()
            }

            inputStream.close()
        } catch (e: Exception) {
            Log.e("ogl-importactivity-gpx", "Failed to parse GPX file: ${e.message}", e)
        }

    }
}