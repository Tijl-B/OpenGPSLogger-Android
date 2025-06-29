package eu.tijlb.opengpslogger.ui.fragment

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
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
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.databinding.FragmentImageGeneratorBinding
import eu.tijlb.opengpslogger.model.database.boundingbox.BoundingBoxDbHelper
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.database.settings.AdvancedFiltersHelper
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.model.dto.query.PointsQuery
import eu.tijlb.opengpslogger.ui.dialog.ZoomableImageDialog
import eu.tijlb.opengpslogger.ui.view.ImageRendererView
import eu.tijlb.opengpslogger.ui.view.bitmap.OsmImageBitmapRenderer
import eu.tijlb.opengpslogger.ui.view.bitmap.PointsBitmapRenderer
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

private const val BOUNDING_BOX_NONE = "None"

class ImageGeneratorFragment : Fragment(), DatePickerFragment.OnDateSelectedListener {

    private lateinit var dataSourcesDeleteButton: ImageButton
    private lateinit var dataSourcesSpinner: Spinner
    private lateinit var pointsProgressBar: ProgressBar
    private lateinit var tilesProgressBar: ProgressBar
    private lateinit var imageRendererView: ImageRendererView

    private lateinit var advancedFiltersHelper: AdvancedFiltersHelper
    private lateinit var minAccuracyChangedListener: OnSharedPreferenceChangeListener

    private var selectedDataSource = DATASOURCE_ALL
        set(value) {
            field = value
            imageRendererView.dataSource = value
            initialiseBeginAndEndTimeAsync()
            resetProgressBars()
            updateDataSourceDeleteButtonVisibility()
            imageRendererView.redrawPointsAndOsm()
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
            initialiseBeginAndEndTimeAsync()
            resetProgressBars()
            imageRendererView.redrawPointsAndOsm()
        }

    private lateinit var locationDbHelper: LocationDbHelper
    private lateinit var boundingBoxDbHelper: BoundingBoxDbHelper

    private var showingZoomableImage = false

    private var _binding: FragmentImageGeneratorBinding? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageGeneratorBinding.inflate(inflater, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        locationDbHelper = LocationDbHelper.getInstance(requireContext())
        boundingBoxDbHelper = BoundingBoxDbHelper(requireContext())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
        dataSourcesDeleteButton = view.findViewById(R.id.button_data_source_delete)
        dataSourcesSpinner = view.findViewById(R.id.spinner_datasources)
        pointsProgressBar = view.findViewById(R.id.pointsProgressBar)
        tilesProgressBar = view.findViewById(R.id.tilesProgressBar)

        advancedFiltersHelper = AdvancedFiltersHelper(requireContext())

        imageRendererView.minAccuracy = advancedFiltersHelper.getMinAccuracy()
        imageRendererView.minAngle = advancedFiltersHelper.getMinAngle()

        minAccuracyChangedListener = advancedFiltersHelper.registerMinAccuracyChangedListener {
            Log.d("ogl-homefragment", "AdvancedFiltersChangedLister triggered")
            imageRendererView.minAccuracy = advancedFiltersHelper.getMinAccuracy()
            imageRendererView.minAngle = advancedFiltersHelper.getMinAngle()
            initialiseBeginAndEndTimeAsync()
            resetProgressBars()
            imageRendererView.redrawPointsAndOsm()
        }

        imageRendererView.onPointProgressUpdateListener =
            object : PointsBitmapRenderer.OnPointProgressUpdateListener {
                override fun onPointProgressMax(max: Int) {
                    pointsProgressBar.max = max
                }

                override fun onPointProgressUpdate(progress: Int) {
                    pointsProgressBar.progress = progress
                }
            }

        imageRendererView.onTileProgressUpdateListener =
            object : OsmImageBitmapRenderer.OnTileProgressUpdateListener {
                override fun onTileProgressMax(max: Int) {
                    Log.d("ogl-homefragment-tile", "tile progress max: $max")
                    tilesProgressBar.max = max
                }

                override fun onTileProgressUpdate(progress: Int) {
                    Log.d("ogl-homefragment-tile", "tile progress: $progress")
                    tilesProgressBar.progress = progress
                }
            }

        imageRendererView.setOnClickListener { showZoomableImage() }
        imageRendererView.pointsRenderWidth = 4000

        initialiseBeginAndEndTimeAsync()
        initialiseDatasourcesSpinnerAsync()
        imageRendererView.redrawPointsAndOsm()
    }

    private fun initialiseDatasourcesSpinnerAsync() {
        viewLifecycleOwner.lifecycleScope.launch {
            val datasources = withContext(Dispatchers.IO) {
                locationDbHelper.getDataSources()
            }
            setUpDataSourcesSpinner(datasources)
        }
    }

    private fun setUpDataSourcesSpinner(datasources: List<String>) {
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

    private fun initialiseBeginAndEndTimeAsync() {
        val query = PointsQuery(
            dataSource = selectedDataSource,
            bbox = inputBbox,
            minAccuracy = advancedFiltersHelper.getMinAccuracy(),
            minAngle = advancedFiltersHelper.getMinAngle()
        )
        viewLifecycleOwner.lifecycleScope.launch {
            var (sTime, eTime) = Pair(startTime, endTime)
            withContext(Dispatchers.IO) {
                val (youngestPointUnix, oldestPointUnix) = locationDbHelper.getTimeRange(query)

                sTime = Instant.ofEpochMilli(youngestPointUnix)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                eTime = Instant.ofEpochMilli(oldestPointUnix)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            }
            startTime = sTime
            endTime = eTime
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        advancedFiltersHelper.deregisterAdvancedFiltersChangedListener(minAccuracyChangedListener)
        _binding = null
    }

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
                imageRendererView.redrawPointsAndOsm()
            }

            "to" -> {
                Log.d("ogl-homefragment-date", "Selected 'to' date: $localDate")
                endTime = localDate
                imageRendererView.redrawPointsAndOsm()
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
        val deleteButton = dialogView.findViewById<ImageButton>(R.id.button_presets_delete)

        populateBoundingBoxSprinner(spinner, deleteButton)

        deleteButton.setOnClickListener {
            boundingBoxDbHelper.delete(spinner.selectedItem.toString())
            populateBoundingBoxSprinner(spinner, deleteButton)
        }

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
                    if (topLeftValue.isEmpty() && bottomRightValue.isEmpty() && spinnerValue != BOUNDING_BOX_NONE) {
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

    private fun populateBoundingBoxSprinner(spinner: Spinner, deleteButton: ImageButton) {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf(BOUNDING_BOX_NONE) + boundingBoxDbHelper.getNames()
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selectedItem = parent?.getItemAtPosition(position)
                Log.d("ogl-homefragment", "Selected item: $selectedItem")
                selectedItem?.toString()
                    ?.let {
                        if (it != BOUNDING_BOX_NONE) {
                            deleteButton.visibility = View.VISIBLE
                        } else {
                            deleteButton.visibility = View.INVISIBLE
                        }
                    }
                parent?.post { parent.clearFocus() }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                parent?.post { parent.clearFocus() }
            }
        }
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

    private fun showZoomableImage() {
        if (!showingZoomableImage) {
            showingZoomableImage = true
            val width = 4000
            val aspectRatio = imageRendererView.aspectRatio
            val height = (width / aspectRatio).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            CoroutineScope(Dispatchers.Default).launch {
                withContext(Dispatchers.IO) {
                    imageRendererView.draw(canvas)
                    val dialog = ZoomableImageDialog(bitmap) {
                        showingZoomableImage = false
                    }
                    dialog.show(parentFragmentManager, "zoomableImageDialog")
                }
            }
        }


    }
}
