package eu.tijlb.opengpslogger.model.parser.json

import android.util.JsonReader
import android.util.Log
import eu.tijlb.opengpslogger.model.util.TimeUtil
import eu.tijlb.opengpslogger.ui.fragment.ImportFragment

object LocationHistoryJsonParser {

    private const val LOC_HIST_JSON_FIELD_START_TIME = "startTime"
    private const val LOC_HIST_JSON_FIELD_END_TIME = "endTime"

    private const val LOC_HIST_JSON_FIELD_ACTIVITY = "activity"
    private const val LOC_HIST_JSON_FIELD_ACTIVITY_END_LOC = "end"
    private const val LOC_HIST_JSON_FIELD_START_LOC = "start"

    private const val LOC_HIST_JSON_FIELD_VISIT = "visit"
    private const val LOC_HIST_JSON_FIELD_VISIT_LOC = "placeLocation"

    private const val LOC_HIST_JSON_FIELD_TOP_CANDIDATE = "topCandidate"
    private const val LOC_HIST_JSON_FIELD_TIMELINE_PATH = "timelinePath"
    private const val LOC_HIST_JSON_FIELD_TIMELINE_POINT = "point"
    private const val LOC_HIST_JSON_FIELD_TIMELINE_DURATION = "durationMinutesOffsetFromStartTime"

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
                    LOC_HIST_JSON_FIELD_START_TIME -> startTime = reader.nextString()
                    LOC_HIST_JSON_FIELD_END_TIME -> endTime = reader.nextString()

                    LOC_HIST_JSON_FIELD_ACTIVITY -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                LOC_HIST_JSON_FIELD_START_LOC -> startCoords =
                                    reader.nextString()

                                LOC_HIST_JSON_FIELD_ACTIVITY_END_LOC -> endCoords =
                                    reader.nextString()

                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }

                    LOC_HIST_JSON_FIELD_VISIT -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                LOC_HIST_JSON_FIELD_TOP_CANDIDATE -> {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            LOC_HIST_JSON_FIELD_VISIT_LOC -> endCoords =
                                                reader.nextString()

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

                    LOC_HIST_JSON_FIELD_TIMELINE_PATH -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            var point: String? = null
                            var durationOffset: String? = null

                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    LOC_HIST_JSON_FIELD_TIMELINE_POINT -> point =
                                        reader.nextString()

                                    LOC_HIST_JSON_FIELD_TIMELINE_DURATION -> durationOffset =
                                        reader.nextString()

                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()

                            if (point != null && startTime != null && durationOffset != null) {
                                val offsetMillis =
                                    durationOffset.toLongOrNull()?.times(60_000) ?: 0L
                                val timelineTimestamp =
                                    TimeUtil.iso8601ToUnixMillis(startTime) + offsetMillis
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
                storeGeoCoords(startCoords, startTime, importStart, save)
                Log.d(
                    "ogl-importactivity-json",
                    "Stored startCoords $startCoords at $startTime"
                )
            }
            if (endTime != null && endCoords != null) {
                storeGeoCoords(endCoords, endTime, importStart, save)
                Log.d("ogl-importactivity-json", "Stored endCoords $endCoords at $endTime")
            }

            for ((coords, timestamp) in timelinePoints) {
                storeGeoCoords(coords, timestamp, importStart, save)
                Log.d("ogl-importactivity-json", "Stored timeline point $coords at $timestamp")
            }

            reader.endObject()
        }

        reader.endArray()
    }

    private fun storeGeoCoords(
        geoCoords: String, time: String, importStart: Long,
        save: (ImportFragment.Point, importStart: Long, source: String) -> Unit
    ) {
        val timestampMillis = time.toLongOrNull() ?: TimeUtil.iso8601ToUnixMillis(time)

        parseGeoCoords(geoCoords)
            ?.let { (lat, lon) ->
                val point = ImportFragment.Point(
                    lat = lat,
                    lon = lon,
                    unixTime = timestampMillis
                )
                save(
                    point,
                    importStart,
                    "location_history_json_import"
                )
            } ?: kotlin.run {
            Log.w(
                "ogl-importactivity",
                "Failed to parse geoCoords $geoCoords"
            )
        }
    }

    private fun parseGeoCoords(str: String): Pair<Double, Double>? {
        val coords = str.replace("geo:", "")
            .replace(" ", "")
            .split(",")
            .mapNotNull { coord -> coord.toDoubleOrNull() }
        if (coords.size != 2) return null
        return Pair(coords[0], coords[1])
    }
}