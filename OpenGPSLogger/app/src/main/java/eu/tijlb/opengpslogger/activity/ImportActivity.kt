package eu.tijlb.opengpslogger.activity

import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.JsonReader
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
        Log.d("ogl-importgpxactivity", "Got intent $intent.")
        if (intent?.action == Intent.ACTION_SEND) {

            val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            uri?.let {
                when (intent.type) {
                    GPX_MIMETYPE -> parseAndStoreGpxFile(it)
                    JSON_MIMETYPE -> parseAndStoreRecordsJson(it)
                    else -> Log.d("ogl-importgpxactivity", "Skipping intent $intent")
                }
            }
        } else if (intent?.action == Intent.ACTION_SEND_MULTIPLE) {
            val uriList = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            uriList?.let {
                for (uri in it) {
                    val fileType = contentResolver.getType(uri)
                    if (fileType == GPX_MIMETYPE || isGpxFile(uri)) {
                        Log.d("ogl-importgpxactivity-gpx", "Processing GPX file: $uri")
                        parseAndStoreGpxFile(uri)
                    } else if (fileType == JSON_MIMETYPE) {
                        parseAndStoreRecordsJson(uri)
                        Log.d("ogl-importgpxactivity-json", "Processing JSON file: $uri")
                    } else {
                        Log.d("ogl-importgpxactivity", "Skipping non-GPX file: $uri")
                    }
                }
            }
        } else {
            Log.d("ogl-importgpxactivity", "Ignoring intent $intent.")
        }
    }

    private fun isGpxFile(uri: Uri): Boolean {
        return uri.toString().endsWith(GPX_EXTENSION, ignoreCase = true)
    }

    private fun parseAndStoreRecordsJson(uri: Uri) {
        try {
            val importStart = System.currentTimeMillis()
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val reader = JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))

            locationDbHelper.writableDatabase.beginTransaction()

            Log.d("ogl-importgpxactivity-json", "Start reading json file...")
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    REC_JSON_FIELD_LOCATIONS -> parseRecordsJsonLocations(reader, importStart)
                    else -> reader.skipValue()
                }
            }

            Log.d("ogl-importgpxactivity-json", "Done reading json file...")
            locationDbHelper.writableDatabase.setTransactionSuccessful()
            locationDbHelper.writableDatabase.endTransaction()

            Log.d("ogl-importgpxactivity-json", "JSON Parsing and Database insertion completed.")
            reader.close()
        } catch (e: Exception) {
            Log.e("ogl-importgpxactivity-json", "Failed to parse JSON file: ${e.message}", e)
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
                    "ogl-importgpxactivity-json",
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
            Log.e("ogl-importgpxactivity-gpx", "Failed to parse GPX file: ${e.message}", e)
        }
    }

    private fun storePointInDatabase(
        point: Point,
        importStart: Long,
        source: String,
    ) {
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