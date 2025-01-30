package eu.tijlb.opengpslogger.util

import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import eu.tijlb.opengpslogger.activity.ImportActivity.Point
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

private const val REC_JSON_FIELD_LOCATIONS = "locations"

class JsonParserUtil {
    companion object {
        fun parse(
            inputStream: InputStream,
            save: (Point, importStart: Long, source: String) -> Unit
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
            save: (Point, importStart: Long, source: String) -> Unit
        ) {
            val importStart = System.currentTimeMillis()

            reader.beginObject()
            while (reader.hasNext()) {
                when (val nextName = reader.nextName()) {
                    REC_JSON_FIELD_LOCATIONS -> RecordsJsonParserUtil.parse(reader, importStart, save)
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
}