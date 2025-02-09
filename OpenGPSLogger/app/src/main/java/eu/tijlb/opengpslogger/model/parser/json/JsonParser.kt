package eu.tijlb.opengpslogger.model.parser.json

import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import eu.tijlb.opengpslogger.ui.activity.ImportActivity
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object JsonParser {

    private const val REC_JSON_FIELD_LOCATIONS = "locations"
    private const val TL_JSON_FIELD_RAW_SIGNALS = "rawSignals"
    private const val TL_JSON_FIELD_SEGMENTS = "semanticSegments"

    fun parse(
        inputStream: InputStream,
        save: (ImportActivity.Point, importStart: Long, source: String) -> Unit
    ) {
        try {
            val reader = JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))

            Log.d("ogl-importactivity-json", "Start reading json file...")
            val token = reader.peek()
            when (token) {
                JsonToken.BEGIN_OBJECT -> parseJsonObject(reader, save)
                JsonToken.BEGIN_ARRAY -> LocationHistoryJsonParser.parse(reader, save)
                else -> Log.w("ogl-importactivity-json", "Unknown json token $token")
            }

            Log.d("ogl-importactivity-json", "JSON Parsing and Database insertion completed.")
            reader.close()
        } catch (e: Exception) {
            Log.e("ogl-importactivity-json", "Failed to parse JSON file: ${e.message}", e)
        }
    }

    private fun parseJsonObject(
        reader: JsonReader,
        save: (ImportActivity.Point, importStart: Long, source: String) -> Unit
    ) {

        reader.beginObject()
        while (reader.hasNext()) {
            when (val nextName = reader.nextName()) {
                REC_JSON_FIELD_LOCATIONS -> RecordsJsonParser.parse(reader, save)
                TL_JSON_FIELD_RAW_SIGNALS -> TimelineJsonRawParser.parse(reader, save)
                TL_JSON_FIELD_SEGMENTS -> TimelineJsonSegmentParser.parse(reader, save)
                else -> {
                    Log.d(
                        "ogl-jsonparserutil-parsejsonobject",
                        "Skipping unknown json value $nextName"
                    )
                    reader.skipValue()
                }
            }
        }
    }
}