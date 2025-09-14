package eu.tijlb.opengpslogger.model.parser.json

import android.util.JsonReader
import android.util.Log
import eu.tijlb.opengpslogger.model.util.TimeUtil
import eu.tijlb.opengpslogger.ui.fragment.ImportFragment

object RecordsJsonParser {

    private const val REC_JSON_FIELD_LON = "longitudeE7"
    private const val REC_JSON_GPX_FIELD_LAT = "latitudeE7"
    private const val REC_JSON_FIELD_TIME = "timestamp"
    private const val REC_JSON_FIELD_ACCURACY = "accuracy"

    fun parse(
        reader: JsonReader,
        save: (ImportFragment.Point, importStart: Long, source: String) -> Unit
    ) {
        val importStart = System.currentTimeMillis()

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
                val timestampMillis = TimeUtil.iso8601ToUnixMillis(timestamp)
                val point = ImportFragment.Point(
                    lat = latitude,
                    lon = longitude,
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
