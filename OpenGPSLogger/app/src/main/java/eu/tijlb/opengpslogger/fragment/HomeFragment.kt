package eu.tijlb.opengpslogger.fragment

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import eu.tijlb.opengpslogger.ImageRendererView
import eu.tijlb.opengpslogger.LocationNotificationService
import eu.tijlb.opengpslogger.OsmHelper
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.database.boundingbox.BoundingBoxDbHelper
import eu.tijlb.opengpslogger.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.database.settings.TrackingStatusHelper
import eu.tijlb.opengpslogger.databinding.FragmentHomeBinding
import eu.tijlb.opengpslogger.dto.BBoxDto
import eu.tijlb.opengpslogger.query.PointsQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val DATASOURCE_ALL = "All"

class HomeFragment : Fragment(), DatePickerFragment.OnDateSelectedListener {

    private lateinit var requestLocationButton: Button
    private lateinit var dataSourcesDeleteButton: ImageButton
    private lateinit var dataSourcesSpinner: Spinner
    private lateinit var pointsProgressBar: ProgressBar
    private lateinit var tilesProgressBar: ProgressBar
    private lateinit var imageRendererView: ImageRendererView

    private lateinit var trackingStatusHelper: TrackingStatusHelper
    private lateinit var trackingActiveChangedListener: OnSharedPreferenceChangeListener

    private var selectedDataSource = DATASOURCE_ALL
        set(value) {
            field = value
            imageRendererView.dataSource = value
            initializeBeginAndEndTime()
            resetProgressBars()
            updateDataSourceDeleteButtonVisibility()
        }

    private var startTime = LocalDate.MIN
        set(value) {
            field = value
            imageRendererView.beginTime = value
            binding.pickDateFrom.text = "from ${value.format(DateTimeFormatter.ISO_DATE)}"
            resetProgressBars()
        }
    private var endTime = LocalDate.MAX
        set(value) {
            field = value
            imageRendererView.endTime = value
            binding.pickDateTo.text = "to ${value.format(DateTimeFormatter.ISO_DATE)}"
            resetProgressBars()
        }
    private var inputBbox: BBoxDto? = null
        set(value) {
            field = value
            imageRendererView.inputBbox = value
            initializeBeginAndEndTime()
            resetProgressBars()
        }

    private lateinit var locationDbHelper: LocationDbHelper
    private lateinit var boundingBoxDbHelper: BoundingBoxDbHelper
    private var requestingLocation = false
        set(value) {
            field = value
            updateRequestLocationButton()
        }

    private var _binding: FragmentHomeBinding? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        locationDbHelper = LocationDbHelper.getInstance(requireContext())
        boundingBoxDbHelper = BoundingBoxDbHelper(requireContext())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonFirst.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }

        binding.buttonRequestLocation.setOnClickListener {
            toggleLocationTracking()
        }

        binding.buttonSaveImage.setOnClickListener {
            saveCanvasAsPng(requireContext())
        }

        binding.pickDateFrom.setOnClickListener {
            val newFragment = DatePickerFragment()
            newFragment.setInitialDate(
                startTime.year,
                startTime.monthValue,
                startTime.dayOfMonth
            )
            newFragment.identifier = "from"
            newFragment.listener = this
            newFragment.show(parentFragmentManager, "datePicker")
        }

        binding.pickDateTo.setOnClickListener {
            val newFragment = DatePickerFragment()
            newFragment.setInitialDate(endTime.year, endTime.monthValue, endTime.dayOfMonth)
            newFragment.identifier = "to"
            newFragment.listener = this
            newFragment.show(parentFragmentManager, "datePicker")
        }

        binding.setBoundingBoxButton.setOnClickListener {
            showInputDialog()
        }

        binding.buttonDataSourceDelete.setOnClickListener {
            if (selectedDataSource != DATASOURCE_ALL) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Confirm Deletion")
                    .setMessage("Are you sure you want to delete source $selectedDataSource? Please manually back up your data before deletion.")
                    .setPositiveButton("Yes") { _, _ ->
                        locationDbHelper.deleteData(selectedDataSource)
                    }
                    .setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .create()
                    .show()
            }
        }
        imageRendererView = view.findViewById(R.id.imageRendererView)

        requestLocationButton = view.findViewById(R.id.button_request_location)
        dataSourcesDeleteButton = view.findViewById(R.id.button_data_source_delete)
        dataSourcesSpinner = view.findViewById(R.id.spinner_datasources)
        pointsProgressBar = view.findViewById(R.id.pointsProgressBar)
        tilesProgressBar = view.findViewById(R.id.tilesProgressBar)

        trackingStatusHelper = TrackingStatusHelper(requireContext())
        requestingLocation = trackingStatusHelper.isActive()
        if(requestingLocation) {
            startPollingLocation()
        }
        updateRequestLocationButton()
        trackingActiveChangedListener = trackingStatusHelper.registerActiveChangedListener {
            requestingLocation = it
        }

        imageRendererView.onPointProgressUpdateListener =
            object : ImageRendererView.OnPointProgressUpdateListener {
                override fun onPointProgressMax(max: Int) {
                    pointsProgressBar.max = max
                }

                override fun onPointProgressUpdate(progress: Int) {
                    pointsProgressBar.progress += progress
                }
            }

        imageRendererView.onTileProgressUpdateListener =
            object : OsmHelper.OnTileProgressUpdateListener {
                override fun onTileProgressMax(max: Int) {
                    Log.d("ogl-homefragment-tile", "tile progress max: $max")
                    tilesProgressBar.max = max
                }

                override fun onTileProgressUpdate(progress: Int) {
                    Log.d("ogl-homefragment-tile", "tile progress: $progress")
                    tilesProgressBar.progress = progress
                }
            }

        initializeBeginAndEndTime()
        setUpDataSourcesSpinner()
        imageRendererView.pointsRenderWidth = 4000
    }

    private fun setUpDataSourcesSpinner() {
        val datasources = locationDbHelper.getDataSources()
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf(DATASOURCE_ALL) + datasources
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dataSourcesSpinner.adapter = adapter

        dataSourcesSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selectedItem = parent?.getItemAtPosition(position)
                Log.d("ogl-homefragment-datasource", "Selected item: $selectedItem")
                selectedItem?.let { selectedDataSource = it.toString() }
                parent?.post { parent.clearFocus() }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                Log.d("ogl-homefragment-datasource", "No item selected")
                parent?.post { parent.clearFocus() }
            }
        }
    }

    private fun initializeBeginAndEndTime() {
        val query = PointsQuery(
            startDateMillis = 0,
            endDateMillis = Long.MAX_VALUE,
            dataSource = selectedDataSource,
            bbox = inputBbox
        )
        val (youngestPointUnix, oldestPointUnix) = locationDbHelper.getTimeRange(query)

        startTime = Instant.ofEpochMilli(youngestPointUnix)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        endTime = Instant.ofEpochMilli(oldestPointUnix)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        imageRendererView.resetIfDrawn()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun toggleLocationTracking() {
        requestLocationButton.text = "Starting tracking"
        if (checkAndRequestPermission(Manifest.permission.ACCESS_FINE_LOCATION, 101)) {
            requestLocationButton.text = "Location permission missing"
            return
        }
        if (checkAndRequestPermission(Manifest.permission.POST_NOTIFICATIONS, 102)) {
            requestLocationButton.text = "Notification permission missing"
            return
        }

        if (!requestingLocation) {
            startPollingLocation()
        } else {
            stopPollingLocation()
        }

    }

    private fun stopPollingLocation() {
        Log.d("ogl-homefragment-location", "Stop polling location")
        val stopIntent = Intent(requireContext(), LocationNotificationService::class.java)
        requireContext().stopService(stopIntent)

        requestingLocation = false
    }

    private fun startPollingLocation() {
        Log.d("ogl-homefragment-location", "Start polling location")
        val intent = Intent(requireContext(), LocationNotificationService::class.java)
        ContextCompat.startForegroundService(requireContext(), intent)

        requestingLocation = true
    }

    private fun checkAndRequestPermission(permission: String, requestCode: Int): Boolean {
        if (!hasPermission(permission)) {
            requestPermission(permission, requestCode)
            return true
        }
        return false
    }

    private fun requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(permission),
            requestCode
        )
    }

    private fun hasPermission(permission: String) = ActivityCompat.checkSelfPermission(
        requireContext(),
        permission,
    ) == PackageManager.PERMISSION_GRANTED

    fun saveCanvasAsPng(context: Context, width: Int = 4000) {
        binding.buttonSaveImage.text = "Saving image..."
        val aspectRatio = imageRendererView.aspectRatio
        val height = (width / aspectRatio).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        GlobalScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.IO) {
                imageRendererView.draw(canvas)
                saveImageToMediaStore(context, bitmap)
                binding.buttonSaveImage.text = "Image saved!"
            }
        }
    }

    fun saveImageToMediaStore(context: Context, bitmap: Bitmap) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "image_${System.currentTimeMillis()}.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                Log.d("ogl-homefragment-export", "Image saved successfully to Pictures")
            }
        } ?: run {
            Log.e("ogl-homefragment-export", "Failed to create media file")
        }
    }

    override fun onDateSelected(identifier: String, localDate: LocalDate) {
        when (identifier) {
            "from" -> {
                Log.d("ogl-homefragment-date", "Selected 'from' date: $localDate")
                startTime = localDate
                imageRendererView.resetIfDrawn()
            }

            "to" -> {
                Log.d("ogl-homefragment-date", "Selected 'to' date: $localDate")
                endTime = localDate
                imageRendererView.resetIfDrawn()
            }
        }
    }

    private fun parseLatLon(coordsInput: String): Pair<Double, Double>? {
        Log.d("ogl-homefragment-coords", "Decoding $coordsInput")
        val coords = coordsInput.replace(" ", "")
            .split(",")
            .mapNotNull { it.toDoubleOrNull() }
        if (coords.size != 2) {
            return null
        }
        return Pair(coords[0], coords[1])
    }

    private fun showInputDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_bounding_box_input, null)

        val topLeftEditText = dialogView.findViewById<EditText>(R.id.text_topLeft)
        val bottomRightEditText = dialogView.findViewById<EditText>(R.id.text_bottomRight)
        val nameEditText = dialogView.findViewById<EditText>(R.id.name)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinner_presets)

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf("None") + boundingBoxDbHelper.getNames()
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        AlertDialog.Builder(requireContext())
            .setTitle("Enter coordinates")
            .setView(dialogView)
            .setPositiveButton("Submit") { dialog, _ ->
                dialog.dismiss()
                CoroutineScope(Dispatchers.Default).launch {
                    var topLeftValue = topLeftEditText.text.toString()
                    var bottomRightValue = bottomRightEditText.text.toString()
                    val nameValue = nameEditText.text.toString()
                    val spinnerValue = spinner.selectedItem.toString()
                    Log.d(
                        "ogl-homefragment-coords",
                        "TopLeft input: $topLeftValue, BottomRight input: $bottomRightValue, Name input: $nameValue, Spinner input: $spinnerValue"
                    )
                    if (topLeftValue.isEmpty() && bottomRightValue.isEmpty() && spinnerValue != "None") {
                        val bbox = boundingBoxDbHelper.get(spinnerValue)
                        bbox?.let {
                            topLeftValue =
                                it.first.first.toString() + "," + it.first.second.toString()
                            bottomRightValue =
                                it.second.first.toString() + "," + it.second.second.toString()
                        }
                    }

                    val bbox = getInputBbox(topLeftValue, bottomRightValue)
                    if (nameValue.isNotEmpty()) {
                        bbox?.let { boundingBoxDbHelper.save(it, nameValue) }
                    }
                    inputBbox = bbox
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun getInputBbox(topLeft: String, bottomRight: String): BBoxDto? {
        val topLeftCoords = parseLatLon(topLeft)
        val bottomRightCoords = parseLatLon(bottomRight)
        if (topLeftCoords != null && bottomRightCoords != null) {
            return BBoxDto(
                bottomRightCoords.first,
                topLeftCoords.first,
                topLeftCoords.second,
                bottomRightCoords.second
            )
        }
        return null
    }

    private fun updateDataSourceDeleteButtonVisibility() {
        if (selectedDataSource == DATASOURCE_ALL) {
            dataSourcesDeleteButton.visibility = View.INVISIBLE
        } else {
            dataSourcesDeleteButton.visibility = View.VISIBLE
        }
    }

    private fun resetProgressBars() {
        pointsProgressBar.progress = 0
        pointsProgressBar.max = 10000000
        tilesProgressBar.progress = 0
        tilesProgressBar.max = 10000000
    }

    private fun updateRequestLocationButton() {
        if (requestingLocation) {
            requestLocationButton.text = "Stop tracking"
        } else {
            requestLocationButton.text = "Start tracking"
        }
    }

}
