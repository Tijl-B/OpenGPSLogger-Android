package eu.tijlb.opengpslogger.ui.activity

import android.content.Intent
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

private const val TAG = "ogl-importactivity"

class ImportActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityImportBinding
    private lateinit var locationDbHelper: LocationDbHelper
    private lateinit var densityMapAdapter: DensityMapAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "ImportActivity.onCreate")
        super.onCreate(savedInstanceState)

        locationDbHelper = LocationDbHelper.getInstance(this)
        densityMapAdapter = DensityMapAdapter.getInstance(this)

        binding = ActivityImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_import)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let { listOf(it) } ?: emptyList()
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { listOf(it) } ?: emptyList()
            }
            else -> emptyList()
        }

        if (uris.isNotEmpty()) {
            val bundle = Bundle().apply {
                putParcelableArrayList("importUris", ArrayList(uris))
            }

            val navController = findNavController(R.id.nav_host_fragment_content_import)
            navController.navigate(R.id.fragment_import, bundle)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_import)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }


}