package eu.tijlb.opengpslogger.ui.activity

import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.databinding.ActivityImportBinding
import eu.tijlb.opengpslogger.model.parser.gpx.GpxParser
import eu.tijlb.opengpslogger.model.parser.json.JsonParser

private const val GPX_EXTENSION = ".gpx"

private const val GPX_MIMETYPE = "application/gpx+xml"
private const val JSON_MIMETYPE = "application/json"

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

                    JSON_MIMETYPE -> parserAndStoreJsonFile(it)
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
                        parserAndStoreJsonFile(uri)
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

    private fun parseAndStoreGpxFile(uri: Uri) {
        contentResolver.openInputStream(uri)
            ?.let {
                locationDbHelper.writableDatabase.beginTransaction()
                GpxParser.parse(it) { point, time, source ->
                    storePointInDatabase(point, time, source)
                }
                locationDbHelper.writableDatabase.setTransactionSuccessful()
                locationDbHelper.writableDatabase.endTransaction()
            }
    }

    private fun parserAndStoreJsonFile(uri: Uri) {
        contentResolver.openInputStream(uri)
            ?.let {
                locationDbHelper.writableDatabase.beginTransaction()
                JsonParser.parse(it) { point, time, source ->
                    storePointInDatabase(point, time, source)
                }
                locationDbHelper.writableDatabase.setTransactionSuccessful()
                locationDbHelper.writableDatabase.endTransaction()
            }
    }

    private fun isGpxFile(uri: Uri): Boolean {
        return uri.toString().endsWith(GPX_EXTENSION, ignoreCase = true)
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

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_import)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    data class Point(
        val lat: Double,
        val lon: Double,
        val unixTime: Long?,
        val gpsSpeed: Float? = null,
        val accuracy: Float? = null
    )
}