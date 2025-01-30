package eu.tijlb.opengpslogger.util

import android.util.JsonReader
import android.util.Log
import eu.tijlb.opengpslogger.activity.ImportActivity.Point
import eu.tijlb.opengpslogger.util.TimeUtil.Companion.iso8601ToUnixMillis

private const val REC_JSON_FIELD_LON = "longitudeE7"
private const val REC_JSON_GPX_FIELD_LAT = "latitudeE7"
private const val REC_JSON_FIELD_TIME = "timestamp"
private const val REC_JSON_FIELD_ACCURACY = "accuracy"

class RecordsJsonParserUtil {
    companion object {
        fun parse(
            reader: JsonReader, importStart: Long,
            save: (Point, importStart: Long, source: String) -> Unit
        ) {
            reader.beginArray()

            while (reader.hasNext()) {
                reader.beginObject()

                var longitude: Double? = null
                var latitude: Double? = null
                var timestamp: String? = null
                var accuracy: String? = null

                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        REC_JSON_FIELD_LON -> longitude = reader.nextLong() / 1e7
                        REC_JSON_GPX_FIELD_LAT -> latitude = reader.nextLong() / 1e7
                        REC_JSON_FIELD_TIME -> timestamp = reader.nextString()
                        REC_JSON_FIELD_ACCURACY -> accuracy = reader.nextString()
                        else -> reader.skipValue()
                    }
                }

                if (longitude != null && latitude != null && timestamp != null) {
                    val timestampMillis = iso8601ToUnixMillis(timestamp)
                    val point = Point(
                        lat = latitude.toDouble(),
                        lon = longitude.toDouble(),
                        unixTime = timestampMillis,
                        accuracy = accuracy?.toFloat()
                    )
                    save(
                        point,
                        importStart,
                        "records_json_import"
                    )
                } else {
                    Log.w(
                        "ogl-importactivity-json",
                        "Missing coordinates or timestamp, skipping location."
                    )
                }

                reader.endObject()
            }
            reader.endArray()
        }
    }
}