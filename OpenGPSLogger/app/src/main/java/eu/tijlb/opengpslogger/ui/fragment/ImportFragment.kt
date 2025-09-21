package eu.tijlb.opengpslogger.ui.fragment

import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.databinding.FragmentImportBinding
import eu.tijlb.opengpslogger.model.database.densitymap.DensityMapAdapter
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.parser.ParserInterface
import eu.tijlb.opengpslogger.model.parser.gpx.GpxParser
import eu.tijlb.opengpslogger.model.parser.gpx.ZippedGpxParser
import eu.tijlb.opengpslogger.model.parser.json.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ogl-importfragment"

private const val MIME_TYPE_GPX = "application/gpx+xml"
private const val MIME_TYPE_ZIP = "application/zip"
private const val MIME_TYPE_JSON = "application/json"

private const val EXTENSION_GPX = ".gpx"
private const val EXTENSION_ZIP = ".zip"

class ImportFragment : Fragment(R.layout.fragment_import) {

    private var _binding: FragmentImportBinding? = null
    private val binding get() = _binding!!

    private lateinit var locationDbHelper: LocationDbHelper
    private lateinit var densityMapAdapter: DensityMapAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationDbHelper = LocationDbHelper.getInstance(requireContext())
        densityMapAdapter = DensityMapAdapter.getInstance(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.buttonContinue.setOnClickListener {
            requireActivity().finish()
        }

        val uris = arguments?.getParcelableArrayList<Uri>("importUris") ?: return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            for (uri in uris) {
                handleUri(uri)
            }

            withContext(Dispatchers.Main) {
                binding.textviewFirst.text = "Import finished!"
                binding.buttonContinue.visibility = View.VISIBLE
            }
        }
    }

    private fun handleUri(uri: Uri) {
        val fileType = requireContext().contentResolver.getType(uri)
        if (fileType == MIME_TYPE_GPX || uri.toString()
                .endsWith(EXTENSION_GPX, ignoreCase = true)
        ) {
            parseAndStoreGpxFile(uri)
        } else if (fileType == MIME_TYPE_ZIP || uri.toString()
                .endsWith(EXTENSION_ZIP, ignoreCase = true)
        ) {
            parseAndStoreZipFile(uri)
        } else if (fileType == MIME_TYPE_JSON) {
            parseAndStoreJsonFile(uri)
        } else {
            Log.d(TAG, "Skipping unsupported file type: $uri")
        }
    }

    private fun parseAndStoreZipFile(uri: Uri) {
        Log.d(TAG, "Processing ZIP file: $uri")
        parse(uri, ZippedGpxParser)
    }

    private fun parseAndStoreGpxFile(uri: Uri) {
        Log.d(TAG, "Processing GPX file: $uri")
        parse(uri, GpxParser)
    }

    private fun parseAndStoreJsonFile(uri: Uri) {
        Log.d(TAG, "Processing JSON file: $uri")
        parse(uri, JsonParser)
    }

    private fun parse(uri: Uri, parser: ParserInterface) {
        requireContext().contentResolver.openInputStream(uri)
            ?.let {

                locationDbHelper.writableDatabase.beginTransaction()
                parser.parse(it) { point, time, source ->
                    storePointInDatabase(point, time, source)
                }
                locationDbHelper.writableDatabase.setTransactionSuccessful()
                locationDbHelper.writableDatabase.endTransaction()
            }
    }

    private fun storePointInDatabase(
        point: Point,
        importStart: Long,
        source: String,
    ) {
        Log.d(TAG, "Storing point $point")
        val location = Location(source).apply {
            latitude = point.lat
            longitude = point.lon
            point.unixTime?.let { time = it }
            point.gpsSpeed?.let { speed = it }
            point.accuracy?.let { accuracy = it }
        }
        locationDbHelper.save(location, "$source::$importStart")
        densityMapAdapter.addLocation(location)
    }

    data class Point(
        val lat: Double,
        val lon: Double,
        val unixTime: Long?,
        val gpsSpeed: Float? = null,
        val accuracy: Float? = null
    )
}