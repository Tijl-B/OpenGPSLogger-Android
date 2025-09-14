package eu.tijlb.opengpslogger.model.parser.json

import android.util.JsonReader
import android.util.Log
import eu.tijlb.opengpslogger.model.util.TimeUtil
import eu.tijlb.opengpslogger.ui.fragment.ImportFragment

object TimelineJsonRawParser {
    private const val TL_JSON_FIELD_POSITION = "position"
    private const val TL_JSON_FIELD_LAT_LNG = "LatLng"
    private const val TL_JSON_FIELD_ACCURACY = "accuracyMeters"
    private const val TL_JSON_FIELD_ALTITUDE = "altitudeMeters"
    private const val TL_JSON_FIELD_TIMESTAMP = "timestamp"
    private const val TL_JSON_FIELD_SPEED = "speedMetersPerSecond"
    private const val TL_JSON_FIELD_SOURCE = "source"

    fun parse(
        reader: JsonReader,
        save: (ImportFragment.Point, importStart: Long, source: String) -> Unit
    ) {
        val importStart = System.currentTimeMillis()

        reader.beginArray()

        while (reader.hasNext()) {
            reader.beginObject()

            var timestamp: String? = null
            var coords: String? = null
            var accuracy: Float? = null
            var speed: Float? = null

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    TL_JSON_FIELD_POSITION -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                TL_JSON_FIELD_LAT_LNG -> coords = reader.nextString()
                                TL_JSON_FIELD_ACCURACY -> accuracy =
                                    reader.nextDouble().toFloat()

                                TL_JSON_FIELD_ALTITUDE -> reader.skipValue()
                                TL_JSON_FIELD_TIMESTAMP -> timestamp = reader.nextString()
                                TL_JSON_FIELD_SPEED -> speed = reader.nextDouble().toFloat()
                                TL_JSON_FIELD_SOURCE -> reader.skipValue()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }

                    else -> reader.skipValue()
                }
            }

            if (timestamp != null && coords != null) {
                storeCoords(coords, timestamp, importStart, speed, accuracy, save)
                Log.d("ogl-importactivity-json", "Stored coords $coords at $timestamp")
            }

            reader.endObject()
        }

        reader.endArray()
    }

    private fun storeCoords(
        geoCoords: String, time: String, importStart: Long, speed: Float?, accuracy: Float?,
        save: (ImportFragment.Point, importStart: Long, source: String) -> Unit
    ) {
        val timestampMillis = time.toLongOrNull() ?: TimeUtil.iso8601ToUnixMillis(time)

        parseCoords(geoCoords)
            ?.let { (lat, lon) ->
                val point = ImportFragment.Point(
                    lat = lat,
                    lon = lon,
                    unixTime = timestampMillis,
                    gpsSpeed = speed,
                    accuracy = accuracy
                )
                save(point, importStart, "timeline_json_import_raw")
            } ?: run {
            Log.w("ogl-importactivity", "Failed to parse geoCoords $geoCoords")
        }
    }

    private fun parseCoords(str: String): Pair<Double, Double>? {
        val coords = str.replace("[^0-9.,-]".toRegex(), "")
            .split(",")
            .mapNotNull { coord -> coord.toDoubleOrNull() }

        if (coords.size != 2) return null
        return Pair(coords[0], coords[1])
    }
}
