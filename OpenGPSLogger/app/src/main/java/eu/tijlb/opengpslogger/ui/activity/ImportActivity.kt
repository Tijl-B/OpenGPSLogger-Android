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
import eu.tijlb.opengpslogger.databinding.ActivityImportBinding
import eu.tijlb.opengpslogger.model.database.densitymap.DensityMapAdapter
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.parser.gpx.GpxParser
import eu.tijlb.opengpslogger.model.parser.json.JsonParser

private const val GPX_EXTENSION = ".gpx"

private const val GPX_MIMETYPE = "application/gpx+xml"
private const val JSON_MIMETYPE = "application/json"

private const val TAG = "ogl-importactivity"

class ImportActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityImportBinding
    private lateinit var locationDbHelper: LocationDbHelper
    private lateinit var densityMapAdapter: DensityMapAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)

        locationDbHelper = LocationDbHelper.getInstance(this)
        densityMapAdapter = DensityMapAdapter.getInstance(this)
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
        Log.d(TAG, "Got intent $intent.")
        when(intent?.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                uri?.let { handleUri(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uriList = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                uriList?.let {
                    for (uri in it) {
                        handleUri(uri)
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                uri?.let { handleUri(it) }
            }
            else -> Log.w(TAG, "Ignoring intent $intent.")
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun handleUri(it: Uri): Any {
        val fileType = contentResolver.getType(it)
        return if (fileType == GPX_MIMETYPE || isGpxFile(it)) {
            Log.d(TAG, "Processing GPX file: $it")
            parseAndStoreGpxFile(it)
        } else if (fileType == JSON_MIMETYPE) {
            Log.d(TAG, "Processing JSON file: $it")
            parserAndStoreJsonFile(it)
        } else {
            Log.d(TAG, "Skipping unsupported file type: $it")
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
        Log.d(TAG, "Storing point $point")
        var location = Location(source).apply {
            latitude = point.lat
            longitude = point.lon
            point.unixTime?.let { time = it }
            point.gpsSpeed?.let { speed = it }
            point.accuracy?.let { accuracy = it }
        }
        locationDbHelper.save(location, "$source::$importStart")
        densityMapAdapter.addLocation(location)
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