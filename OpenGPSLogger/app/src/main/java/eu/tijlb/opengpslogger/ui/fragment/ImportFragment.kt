package eu.tijlb.opengpslogger.ui.fragment

import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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

private const val EXTENSION_GPX = "gpx"
private const val EXTENSION_ZIP = "zip"

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
        locationDbHelper = LocationDbHelper.getInstance(requireContext().applicationContext)
        densityMapAdapter = DensityMapAdapter.getInstance(requireContext().applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.buttonContinue.setOnClickListener {
            requireActivity().finish()
        }

        val uris = arguments?.getParcelableArrayList<Uri>("importUris") ?: return

        val totalFiles = uris.size
        var currentFile = 1
        var totalPointsImported = 0


        updateFileProgress(0, totalFiles)
        updatePointsProgress(0)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            for (uri in uris) {
                withContext(Dispatchers.Main) {
                    updateFileProgress(currentFile, totalFiles)
                    currentFile += 1
                }
                handleUri(uri) { points ->
                    totalPointsImported += points
                    if (totalPointsImported % 1000 == 0) {
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                            updatePointsProgress(totalPointsImported)
                        }
                    }

                }
            }

            withContext(Dispatchers.Main) {
                updatePointsProgress(totalPointsImported)
                binding.textviewFirst.text = "Import finished!"
                binding.buttonContinue.visibility = View.VISIBLE
            }
        }
    }

    private fun updateFileProgress(currentFile: Int, totalFiles: Int) {
        binding.textviewFileProgress.text =
            getString(R.string.file_import_progress, currentFile, totalFiles)
    }

    private fun updatePointsProgress(totalPointsImported: Int) {
        binding.textviewPointsProgress.text =
            getString(R.string.processed_points_progress, totalPointsImported)
    }

    private fun handleUri(uri: Uri, pointsCallback: (Int) -> Unit) {
        val fileType = requireContext().contentResolver.getType(uri)
        val fileName = getFileNameFromUri(uri)
        val fileExtension = fileName?.substringAfterLast('.', "") ?: ""
        val parser = when {
            fileType == MIME_TYPE_GPX || fileExtension.equals(EXTENSION_GPX, ignoreCase = true)
                -> GpxParser

            fileType == MIME_TYPE_ZIP || fileExtension.equals(EXTENSION_ZIP, ignoreCase = true)
                -> ZippedGpxParser

            fileType == MIME_TYPE_JSON -> JsonParser
            else -> {
                Log.d(
                    TAG,
                    "Skipping unsupported file type: $fileType for file $fileName with extension $fileExtension and uri $uri"
                )
                null
            }
        }

        parser?.let {
            Log.d(TAG, "Processing file with parser ${parser.javaClass.simpleName}: $uri")
            parse(uri, parser, pointsCallback)
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        requireContext().contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                return cursor.getString(index)
            }
        }
        return null
    }

    private fun parse(uri: Uri, parser: ParserInterface, pointsCallback: (Int) -> Unit) {
        requireContext().contentResolver.openInputStream(uri)
            ?.let {

                locationDbHelper.writableDatabase.beginTransaction()
                parser.parse(it) { point, time, source ->
                    pointsCallback(1)
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