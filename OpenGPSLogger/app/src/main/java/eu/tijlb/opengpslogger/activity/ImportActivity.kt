package eu.tijlb.opengpslogger.activity

import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.databinding.ActivityImportBinding
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.time.Instant

private const val GPX_EXTENSION = ".gpx"

private const val GPX_MIMETYPE = "application/gpx+xml"
private const val JSON_MIMETYPE = "application/json"

private const val REC_JSON_FIELD_LOCATIONS = "locations"
private const val REC_JSON_FIELD_LON = "longitudeE7"
private const val REC_JSON_GPX_FIELD_LAT = "latitudeE7"
private const val REC_JSON_FIELD_TIME = "timestamp"
private const val REC_JSON_FIELD_ACCURACY = "accuracy"

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

private const val GPX_FIELD_TRACKPOINT = "trkpt"
private const val GPX_FIELD_TIME = "time"
private const val GPX_ATTR_LAT = "lat"
private const val GPX_ATTR_LON = "lon"
private const val GPX_FIELD_SPEED = "speed"

class ImportActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityImportBinding
    private lateinit var locationDbHelper: LocationDbHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationDbHelper = LocationDbHelper.getInstance(this)
        handleIncomingIntent(intent)

        binding = ActivityImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_import)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        Log.d("ogl-importactivity", "Got intent $intent.")
        if (intent?.action == Intent.ACTION_SEND) {

            val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            uri?.let {
                when (intent.type) {
                    GPX_MIMETYPE -> parseAndStoreGpxFile(it)
                    JSON_MIMETYPE -> parseAndStoreRecordsJson(it)
                    else -> Log.d("ogl-importactivity", "Skipping intent $intent")
                }
            }
        } else if (intent?.action == Intent.ACTION_SEND_MULTIPLE) {
            val uriList = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            uriList?.let {
                for (uri in it) {
                    val fileType = contentResolver.getType(uri)
                    if (fileType == GPX_MIMETYPE || isGpxFile(uri)) {
                        Log.d("ogl-importactivity-gpx", "Processing GPX file: $uri")
                        parseAndStoreGpxFile(uri)
                    } else if (fileType == JSON_MIMETYPE) {
                        parseAndStoreRecordsJson(uri)
                        Log.d("ogl-importactivity-json", "Processing JSON file: $uri")
                    } else {
                        Log.d("ogl-importactivity", "Skipping non-GPX file: $uri")
                    }
                }
            }
        } else {
            Log.d("ogl-importactivity", "Ignoring intent $intent.")
        }
    }

    private fun isGpxFile(uri: Uri): Boolean {
        return uri.toString().endsWith(GPX_EXTENSION, ignoreCase = true)
    }

    private fun parseAndStoreRecordsJson(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val reader = JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))

            locationDbHelper.writableDatabase.beginTransaction()

            Log.d("ogl-importactivity-json", "Start reading json file...")
            val token = reader.peek()
            when (token) {
                JsonToken.BEGIN_OBJECT -> parseRecordsJson(reader)
                JsonToken.BEGIN_ARRAY -> parseLocationHistoryJson(reader)
                else -> Log.w("ogl-importactivity-json", "Unknown json token $token")
            }


            Log.d("ogl-importactivity-json", "Done reading json file...")
            locationDbHelper.writableDatabase.setTransactionSuccessful()
            locationDbHelper.writableDatabase.endTransaction()

            Log.d("ogl-importactivity-json", "JSON Parsing and Database insertion completed.")
            reader.close()
        } catch (e: Exception) {
            Log.e("ogl-importactivity-json", "Failed to parse JSON file: ${e.message}", e)
        }
    }

    private fun parseLocationHistoryJson(reader: JsonReader) {
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
                                LOC_HIST_JSON_FIELD_START_LOC -> startCoords = reader.nextString()
                                LOC_HIST_JSON_FIELD_ACTIVITY_END_LOC -> endCoords = reader.nextString()
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
                                            LOC_HIST_JSON_FIELD_VISIT_LOC -> endCoords = reader.nextString()
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
                                    LOC_HIST_JSON_FIELD_TIMELINE_POINT -> point = reader.nextString()
                                    LOC_HIST_JSON_FIELD_TIMELINE_DURATION -> durationOffset = reader.nextString()
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()

                            if (point != null && startTime != null && durationOffset != null) {
                                val offsetMillis = durationOffset.toLongOrNull()?.times(60_000) ?: 0L
                                val timelineTimestamp = iso8601ToUnixMillis(startTime) + offsetMillis
                                timelinePoints.add(Pair(point, timelineTimestamp.toString()))
                            }
                        }
                        reader.endArray()
                    }

                    else -> {
                        Log.d("ogl-importactivity-json", "Skipping unknown field: ${reader.peek()}")
                        reader.skipValue()
                    }
                }
            }

            if (startTime != null && startCoords != null) {
                storeGeoCoords(startCoords, startTime, importStart)
                Log.d("ogl-importactivity-json", "Stored startCoords $startCoords at $startTime")
            }
            if (endTime != null && endCoords != null) {
                storeGeoCoords(endCoords, endTime, importStart)
                Log.d("ogl-importactivity-json", "Stored endCoords $endCoords at $endTime")
            }

            for ((coords, timestamp) in timelinePoints) {
                storeGeoCoords(coords, timestamp, importStart)
                Log.d("ogl-importactivity-json", "Stored timeline point $coords at $timestamp")
            }

            reader.endObject()
        }

        reader.endArray()
    }


    private fun storeGeoCoords(geoCoords: String, time: String, importStart: Long) {
        val timestampMillis = time.toLongOrNull()?:iso8601ToUnixMillis(time)

        parseGeoCoords(geoCoords)
            ?.let { (lat, lon) ->
                val point = Point(
                    lat = lat,
                    lon = lon,
                    unixTime = timestampMillis
                )
                storePointInDatabase(
                    point,
                    importStart,
                    "location_history_json_import"
                )
            } ?: kotlin.run { Log.w("ogl-importactivity", "Failed to parse geoCoords $geoCoords") }
    }

    private fun parseGeoCoords(str: String): Pair<Double, Double>? {
        val coords = str.replace("geo:", "")
            .replace(" ", "")
            .split(",")
            .mapNotNull { coord -> coord.toDoubleOrNull() }
        if (coords.size != 2) return null
        return Pair(coords[0], coords[1])
    }

    private fun parseRecordsJson(reader: JsonReader) {
        val importStart = System.currentTimeMillis()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                REC_JSON_FIELD_LOCATIONS -> parseRecordsJsonLocations(reader, importStart)
                else -> reader.skipValue()
            }
        }
    }

    private fun parseRecordsJsonLocations(reader: JsonReader, importStart: Long) {
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
                storePointInDatabase(
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

    private fun parseAndStoreGpxFile(uri: Uri) {
        try {
            val importStart = System.currentTimeMillis()
            val inputStream = contentResolver.openInputStream(uri)
            val parserFactory = XmlPullParserFactory.newInstance()
            val parser = parserFactory.newPullParser()
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            var latitude: String? = null
            var longitude: String? = null
            var time: Long? = null
            var speed: String? = null

            locationDbHelper.writableDatabase.beginTransaction()
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tagName) {
                            GPX_FIELD_TRACKPOINT -> {
                                latitude = parser.getAttributeValue(null, GPX_ATTR_LAT)
                                longitude = parser.getAttributeValue(null, GPX_ATTR_LON)
                            }

                            GPX_FIELD_TIME -> time = iso8601ToUnixMillis(parser.nextText())
                            GPX_FIELD_SPEED -> speed = parser.nextText()
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (tagName == GPX_FIELD_TRACKPOINT && latitude != null && longitude != null) {
                            val point = Point(
                                lat = latitude.toDouble(),
                                lon = longitude.toDouble(),
                                unixTime = time,
                                gpsSpeed = speed?.toFloat()
                            )
                            storePointInDatabase(
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

            inputStream?.close()
            locationDbHelper.writableDatabase.setTransactionSuccessful()
            locationDbHelper.writableDatabase.endTransaction()
        } catch (e: Exception) {
            Log.e("ogl-importactivity-gpx", "Failed to parse GPX file: ${e.message}", e)
        }
    }

    private fun storePointInDatabase(
        point: Point,
        importStart: Long,
        source: String,
    ) {
        Log.d("ogl-importactivity", "Storing point $point")
        var location = Location(source).apply {
            latitude = point.lat
            longitude = point.lon
            point.unixTime?.let { time = it }
            point.gpsSpeed?.let { speed = it }
            point.accuracy?.let { accuracy = it }
        }
        locationDbHelper.save(location, "$source::$importStart")
    }

    private fun iso8601ToUnixMillis(isoDate: String): Long {
        return Instant.parse(isoDate).toEpochMilli()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_import)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    private data class Point(
        val lat: Double,
        val lon: Double,
        val unixTime: Long?,
        val gpsSpeed: Float? = null,
        val accuracy: Float? = null
    )
}