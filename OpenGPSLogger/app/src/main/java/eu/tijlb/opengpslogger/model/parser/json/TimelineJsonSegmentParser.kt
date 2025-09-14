package eu.tijlb.opengpslogger.model.parser.json

import android.util.JsonReader
import android.util.Log
import eu.tijlb.opengpslogger.model.util.TimeUtil
import eu.tijlb.opengpslogger.ui.fragment.ImportFragment

object TimelineJsonSegmentParser {

    private const val TL_JSON_FIELD_START_TIME = "startTime"
    private const val TL_JSON_FIELD_END_TIME = "endTime"

    private const val TL_JSON_FIELD_ACTIVITY = "activity"
    private const val TL_JSON_FIELD_ACTIVITY_END_LOC = "end"
    private const val TL_JSON_FIELD_START_LOC = "start"
    private const val TL_JSON_FIELD_DISTANCE_METERS = "distanceMeters"
    private const val TL_JSON_FIELD_TOP_CANDIDATE = "topCandidate"

    private const val TL_JSON_FIELD_VISIT = "visit"
    private const val TL_JSON_FIELD_VISIT_LOC = "placeLocation"
    private const val TL_JSON_FIELD_TIMELINE_PATH = "timelinePath"
    private const val TL_JSON_FIELD_TIMELINE_POINT = "point"
    private const val TL_JSON_FIELD_TIMELINE_TIME = "time"

    private const val TL_JSON_FIELD_LATLNG = "latLng"

    fun parse(
        reader: JsonReader,
        save: (ImportFragment.Point, importStart: Long, source: String) -> Unit
    ) {
        val importStart = System.currentTimeMillis()

        reader.beginArray()

        while (reader.hasNext()) {
            reader.beginObject()

            var startTime: String? = null
            var endTime: String? = null
            var startCoords: String? = null
            var endCoords: String? = null
            val timelinePoints = mutableListOf<Pair<String, String>>()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    TL_JSON_FIELD_START_TIME -> startTime = reader.nextString()
                    TL_JSON_FIELD_END_TIME -> endTime = reader.nextString()

                    TL_JSON_FIELD_ACTIVITY -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                TL_JSON_FIELD_START_LOC -> {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            TL_JSON_FIELD_LATLNG -> startCoords =
                                                reader.nextString()
                                        }
                                    }
                                    reader.endObject()
                                }

                                TL_JSON_FIELD_ACTIVITY_END_LOC -> {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            TL_JSON_FIELD_LATLNG -> endCoords =
                                                reader.nextString()
                                        }
                                    }
                                    reader.endObject()
                                }

                                TL_JSON_FIELD_DISTANCE_METERS -> reader.nextDouble()
                                TL_JSON_FIELD_TOP_CANDIDATE -> {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        reader.skipValue()
                                    }
                                    reader.endObject()
                                }

                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }

                    TL_JSON_FIELD_VISIT -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                TL_JSON_FIELD_TOP_CANDIDATE -> {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            TL_JSON_FIELD_VISIT_LOC -> {
                                                reader.beginObject()
                                                while (reader.hasNext()) {
                                                    when (reader.nextName()) {
                                                        TL_JSON_FIELD_LATLNG -> endCoords =
                                                            reader.nextString()
                                                    }
                                                }
                                                reader.endObject()
                                            }

                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                }

                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }

                    TL_JSON_FIELD_TIMELINE_PATH -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            var point: String? = null
                            var time: String? = null

                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    TL_JSON_FIELD_TIMELINE_POINT -> point = reader.nextString()
                                    TL_JSON_FIELD_TIMELINE_TIME -> time = reader.nextString()
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()

                            if (point != null && time != null) {
                                val timelineTimestamp = TimeUtil.iso8601ToUnixMillis(time)
                                timelinePoints.add(Pair(point, timelineTimestamp.toString()))
                            }
                        }
                        reader.endArray()
                    }

                    else -> {
                        Log.d(
                            "ogl-importactivity-json",
                            "Skipping unknown field: ${reader.peek()}"
                        )
                        reader.skipValue()
                    }
                }
            }

            if (startTime != null && startCoords != null) {
                storeCoords(startCoords, startTime, importStart, save)
                Log.d(
                    "ogl-importactivity-json",
                    "Stored startCoords $startCoords at $startTime"
                )
            }
            if (endTime != null && endCoords != null) {
                storeCoords(endCoords, endTime, importStart, save)
                Log.d("ogl-importactivity-json", "Stored endCoords $endCoords at $endTime")
            }

            for ((coords, timestamp) in timelinePoints) {
                storeCoords(coords, timestamp, importStart, save)
                Log.d("ogl-importactivity-json", "Stored timeline point $coords at $timestamp")
            }

            reader.endObject()
        }

        reader.endArray()
    }

    private fun storeCoords(
        geoCoords: String, time: String, importStart: Long,
        save: (ImportFragment.Point, importStart: Long, source: String) -> Unit
    ) {
        val timestampMillis = time.toLongOrNull() ?: TimeUtil.iso8601ToUnixMillis(time)

        parseCoords(geoCoords)
            ?.let { (lat, lon) ->
                val point = ImportFragment.Point(
                    lat = lat,
                    lon = lon,
                    unixTime = timestampMillis
                )
                save(
                    point,
                    importStart,
                    "timeline_json_import_segment"
                )
            } ?: kotlin.run {
            Log.w(
                "ogl-importactivity",
                "Failed to parse geoCoords $geoCoords"
            )
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
