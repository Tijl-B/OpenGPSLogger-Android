package eu.tijlb.opengpslogger.ui.fragment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.Bundle
import android.provider.BaseColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import eu.tijlb.opengpslogger.BuildConfig
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.databinding.FragmentDatabaseBinding
import eu.tijlb.opengpslogger.model.broadcast.LocationUpdateReceiver
import eu.tijlb.opengpslogger.model.database.densitymap.DensityMapAdapter
import eu.tijlb.opengpslogger.model.database.location.LocationDatabaseFileProvider
import eu.tijlb.opengpslogger.model.database.location.LocationDbContract
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import eu.tijlb.opengpslogger.model.dto.BBoxDto
import eu.tijlb.opengpslogger.model.dto.query.PointsQuery
import eu.tijlb.opengpslogger.model.exporter.GpxExporter
import eu.tijlb.opengpslogger.ui.activity.ImportActivity
import eu.tijlb.opengpslogger.ui.util.DateTimeUtil
import eu.tijlb.opengpslogger.ui.view.ImageRendererView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "ogl-databasefragment"

class DatabaseFragment : Fragment(R.layout.fragment_database) {

    lateinit var tableLayout: TableLayout

    private var _binding: FragmentDatabaseBinding? = null
    private lateinit var locationDbHelper: LocationDbHelper
    private lateinit var densityMapAdapter: DensityMapAdapter
    private lateinit var gpxExporter: GpxExporter
    private lateinit var locationDatabaseFileProvider: LocationDatabaseFileProvider
    private lateinit var locationReceiver: LocationUpdateReceiver

    private val binding get() = _binding!!

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (!uris.isNullOrEmpty()) {
                val intent = Intent(requireContext(), ImportActivity::class.java).apply {
                    action = Intent.ACTION_SEND_MULTIPLE
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDatabaseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonImport.setOnClickListener {
            filePicker.launch(
                arrayOf(
                    "application/gpx+xml",
                    "application/zip",
                    "text/xml",
                    "application/octet-stream",
                    "application/json"
                )
            )
        }

        binding.buttonImportGuide.setOnClickListener {
            val url = BuildConfig.IMPORT_GUIDE_URL
            val intent = CustomTabsIntent.Builder().build()
            intent.launchUrl(requireContext(), url.toUri())
        }

        tableLayout = view.findViewById(R.id.table_recent_locations)
        locationDatabaseFileProvider = LocationDatabaseFileProvider()
        locationDbHelper = LocationDbHelper.getInstance(requireContext().applicationContext)
        densityMapAdapter = DensityMapAdapter.getInstance(requireContext().applicationContext)
        gpxExporter = GpxExporter(requireContext().applicationContext)

        binding.buttonShare.setOnClickListener {
            locationDatabaseFileProvider.share(requireContext())
        }
        binding.buttonExport.setOnClickListener { 
            openExportPointsByTimeDialog()
        }
        binding.buttonDeleteByTime.setOnClickListener {
            openDeletePointsByTimeDialog()
        }
        binding.buttonDeleteByBox.setOnClickListener {
            openDeletePointsByBoxDialog()
        }
        binding.buttonDeleteDuplicates.setOnClickListener {
            openDeleteDuplicatesDialog()
        }

        locationReceiver = LocationUpdateReceiver().apply {
            setOnLocationReceivedListener { location ->
                location?.let { addNewLocation(it) }
            }
        }

        val filter = IntentFilter("eu.tijlb.LOCATION_RECEIVED")
        requireContext().registerReceiver(locationReceiver, filter, Context.RECEIVER_EXPORTED)

        viewLifecycleOwner.lifecycleScope.launch {
            val tableRows = withContext(Dispatchers.IO) {
                val entities = getLastEntities()
                Log.d(TAG, "Got ${entities.size} entities")
                toTableRows(entities)
            }
            populateTable(tableRows)
        }
    }

    private fun openExportPointsByTimeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_points_by_time, null)
        val fromButton = dialogView.findViewById<Button>(R.id.button_from)
        val toButton = dialogView.findViewById<Button>(R.id.button_to)
        val imageRendererView = dialogView.findViewById<ImageRendererView>(R.id.imageRendererView)
        val selectedPointsTextView =
            dialogView.findViewById<TextView>(R.id.textview_selected_points_value)
        val from: AtomicReference<ZonedDateTime?> = AtomicReference(null)
        val to: AtomicReference<ZonedDateTime?> = AtomicReference(null)
        selectedPointsTextView.text = getString(R.string.selected_points, "0")
        fromButton.setOnClickListener {
            DateTimeUtil.pickDateTime(requireContext()) {
                from.set(it)
                fromButton.text = "From: ${it.format(ISO_LOCAL_DATE_TIME)}"
                to.get()?.let { toTime ->
                    updateCountAndRenderer(imageRendererView, selectedPointsTextView, it, toTime)
                }
            }
        }
        toButton.setOnClickListener {
            DateTimeUtil.pickDateTime(requireContext()) {
                to.set(it)
                toButton.text = "To: ${it.format(ISO_LOCAL_DATE_TIME)}"
                from.get()?.let { fromTime ->
                    updateCountAndRenderer(imageRendererView, selectedPointsTextView, fromTime, it)
                }
            }
        }

        AlertDialog.Builder(context)
            .setTitle("Export as GPX")
            .setView(dialogView)
            .setPositiveButton("Export selected points") { dialog, _ ->
                if (to.get() == null || from.get()?.isAfter(to.get()) ?: true) {
                    Log.d(TAG, "From $from or to $to is invalid, not exporting any points")
                } else {
                    Log.d(TAG, "exporting points from $from to $to")
                    exportPoints(from.get()!!, to.get()!!)
                }
            }
            .setNegativeButton("Discard") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun openDeletePointsByBoxDialog(): Boolean {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_points_by_box, null)
        val selectorFragment = childFragmentManager
            .findFragmentById(R.id.boundingBoxFragmentContainer) as BoundingBoxSelectorFragment
        val selectedPointsTextView =
            dialogView.findViewById<TextView>(R.id.textview_selected_points_value)
        val selectedBbox = AtomicReference<BBoxDto?>(null)
        selectedPointsTextView.text = getString(R.string.selected_points, "0")
        selectorFragment.callback = {
            selectedBbox.set(it)
            updateCount(selectedPointsTextView, it)
        }

        AlertDialog.Builder(context)
            .setTitle("Delete points by box")
            .setView(dialogView)
            .setPositiveButton("Delete selected points") { dialog, _ ->
                selectedBbox.get()?.let {
                    val query = PointsQuery(bbox = it)
                    deletePoints(query)
                }
            }
            .setNegativeButton("Discard") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
        return true
    }

    private fun openDeleteDuplicatesDialog(): Boolean {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_duplicate_points, null)
        val selectedPointsTextView =
            dialogView.findViewById<TextView>(R.id.textview_selectedpoints)
        selectedPointsTextView.text =
            getString(R.string.selected_points, "calculating...")
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                locationDbHelper.countDuplicatePoints()
            }
            selectedPointsTextView.text =
                getString(R.string.selected_points, count.toString())
        }


        AlertDialog.Builder(context)
            .setTitle("Delete duplicate points")
            .setView(dialogView)
            .setPositiveButton("Delete selected points")
            { dialog, _ ->
                lifecycleScope.launch {
                    val count = withContext(Dispatchers.IO) {
                        locationDbHelper.deleteDuplicatePoints()
                    }
                    Toast.makeText(
                        context,
                        "Deleted $count points",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Discard")
            { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
        return true
    }

    private fun updateCount(
        countField: TextView,
        bbox: BBoxDto
    ) {
        countField.text = getString(R.string.selected_points, "calculating...")
        val query = PointsQuery(bbox = bbox)
        calculatePointsCount(query) {
            countField.text = getString(R.string.selected_points, it.toString())
        }
    }

    private fun openDeletePointsByTimeDialog(): Boolean {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_points_by_time, null)
        val fromButton = dialogView.findViewById<Button>(R.id.button_from)
        val toButton = dialogView.findViewById<Button>(R.id.button_to)
        val imageRendererView = dialogView.findViewById<ImageRendererView>(R.id.imageRendererView)
        val selectedPointsTextView =
            dialogView.findViewById<TextView>(R.id.textview_selected_points_value)
        val from: AtomicReference<ZonedDateTime?> = AtomicReference(null)
        val to: AtomicReference<ZonedDateTime?> = AtomicReference(null)
        selectedPointsTextView.text = getString(R.string.selected_points, "0")
        fromButton.setOnClickListener {
            DateTimeUtil.pickDateTime(requireContext()) {
                from.set(it)
                fromButton.text = "From: ${it.format(ISO_LOCAL_DATE_TIME)}"
                to.get()?.let { toTime ->
                    updateCountAndRenderer(imageRendererView, selectedPointsTextView, it, toTime)
                }
            }
        }
        toButton.setOnClickListener {
            DateTimeUtil.pickDateTime(requireContext()) {
                to.set(it)
                toButton.text = "To: ${it.format(ISO_LOCAL_DATE_TIME)}"
                from.get()?.let { fromTime ->
                    updateCountAndRenderer(imageRendererView, selectedPointsTextView, fromTime, it)
                }
            }
        }

        AlertDialog.Builder(context)
            .setTitle("Delete points by time")
            .setView(dialogView)
            .setPositiveButton("Delete selected points") { dialog, _ ->
                if (to.get() == null || from.get()?.isAfter(to.get()) ?: true) {
                    Log.d(TAG, "From $from or to $to is invalid, not deleting any points")
                } else {
                    Log.d(TAG, "Deleting points from $from to $to")
                    deletePoints(from.get()!!, to.get()!!)
                }
            }
            .setNegativeButton("Discard") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
        return true
    }

    private fun updateCountAndRenderer(
        renderer: ImageRendererView,
        countField: TextView,
        from: ZonedDateTime,
        to: ZonedDateTime
    ) {
        renderer.beginTime = from
        renderer.endTime = to
        renderer.pointsRenderWidth = 4000
        renderer.redrawPointsAndOsm()
        countField.text = getString(R.string.selected_points, "calculating...")
        val query = PointsQuery(
            startDateMillis = from.toInstant().toEpochMilli(),
            endDateMillis = to.toInstant().toEpochMilli()
        )
        calculatePointsCount(query) {
            countField.text = getString(R.string.selected_points, it.toString())
        }
    }

    private fun calculatePointsCount(
        query: PointsQuery,
        callback: (Int) -> Unit
    ) {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                locationDbHelper.getPointsCursor(query)
                    .use { it.count }
            }
            callback(count)
        }
    }

    private fun deletePoints(
        from: ZonedDateTime,
        to: ZonedDateTime
    ) {
        val query = PointsQuery(
            startDateMillis = from.toInstant().toEpochMilli(),
            endDateMillis = to.toInstant().toEpochMilli()
        )

        deletePoints(query)
    }

    private fun exportPoints(from: ZonedDateTime, to: ZonedDateTime) {
        val query = PointsQuery(
            startDateMillis = from.toInstant().toEpochMilli(),
            endDateMillis = to.toInstant().toEpochMilli()
        )
        exportPoints(query)
    }

    private fun exportPoints(query: PointsQuery) {
        Toast.makeText(
            context,
            "Export started. Please wait.",
            Toast.LENGTH_SHORT
        ).show()
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                gpxExporter.export(query)
            }
            Toast.makeText(
                context,
                "Created file $file in Downloads",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun deletePoints(query: PointsQuery) {
        Toast.makeText(
            context,
            "Deletion started. Please wait.",
            Toast.LENGTH_SHORT
        ).show()
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                deletePointsFromDensityMap(query)
                locationDbHelper.delete(query)
            }
            Toast.makeText(
                context,
                "Deleted $count points",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private suspend fun deletePointsFromDensityMap(query: PointsQuery) {
        locationDbHelper.getPointsFlow(query)
            .collect { point ->
                densityMapAdapter.deletePoint(point.latitude, point.longitude)
            }
    }

    private fun addNewLocation(location: Location) {
        context?.let { _ ->
            {
                var formattedTime = formatUnixTime(location.time)
                var row = listOf(
                    "N/A",
                    location.latitude.toString(),
                    location.longitude.toString(),
                    location.speedAccuracyMetersPerSecond.toString(),
                    location.speed.toString(),
                    location.accuracy.toString(),
                    formattedTime
                )
                val tableRow = buildTableRow(row)
                tableLayout.addView(tableRow, 1)
            }
        }
    }

    private fun toTableRows(entities: List<List<String>>): List<TableRow> {
        val columnTitles =
            listOf(
                "id",
                "lat",
                "lon",
                "speed (m/s)",
                "speed accuracy",
                "location accuracy",
                "time",
                "source",
                "created on"
            )
        val tail = if (entities.size == 500) listOf("...") else listOf("")
        return (listOf(columnTitles) + entities + listOf(tail)).mapNotNull {
            buildTableRow(it)
        }
    }

    private fun populateTable(tableRows: List<TableRow>) {
        tableRows.forEach { tableLayout.addView(it) }
    }

    private fun buildTableRow(rowData: List<String>): TableRow? {
        return context?.let {
            val tableRow = TableRow(it)

            for (columnData in rowData) {
                val textView = TextView(it)
                textView.text = columnData
                textView.setPadding(16, 16, 16, 16)

                tableRow.addView(textView)
            }
            tableRow
        }
    }

    override fun onDestroyView() {
        requireContext().unregisterReceiver(locationReceiver)
        _binding = null
        super.onDestroyView()
    }

    @SuppressLint("Range")
    private fun getLastEntities(): List<List<String>> {
        val dbHelper = LocationDbHelper.getInstance(requireContext().applicationContext)
        val db = dbHelper.readableDatabase
        val entities = mutableListOf<List<String>>()

        db.rawQuery(
            """
                    SELECT * FROM ${LocationDbContract.TABLE_NAME}
                    ORDER BY ${BaseColumns._ID} DESC
                    LIMIT 500
                """.trimIndent(),
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(BaseColumns._ID)
                val latIndex = cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_LATITUDE)
                val lonIndex = cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_LONGITUDE)
                val speedIndex = cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_SPEED)
                val speedAccuracyIndex =
                    cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_SPEED_ACCURACY)
                val accuracyIndex =
                    cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_ACCURACY)
                val timestampIndex =
                    cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_TIMESTAMP)
                val sourceIndex = cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_SOURCE)
                val createdOnIndex = LocationDbContract.COLUMN_NAME_CREATED_ON

                do {
                    val id = cursor.getLong(idIndex)
                    val latitude = cursor.getString(latIndex)
                    val longitude = cursor.getString(lonIndex)
                    val speed = cursor.getString(speedIndex)
                    val speedAccuracy = cursor.getString(speedAccuracyIndex)
                    val accuracy = cursor.getString(accuracyIndex)
                    val unixTime = cursor.getString(timestampIndex)
                    val source = cursor.getString(sourceIndex)
                    val createdOnUnix = cursor.getString(cursor.getColumnIndex(createdOnIndex))

                    val formattedTime = formatUnixTime(unixTime.toLong())
                    val formattedCreatedOn = formatUnixTime(createdOnUnix.toLong())

                    entities.add(
                        listOf(
                            id.toString(),
                            latitude,
                            longitude,
                            speed,
                            speedAccuracy,
                            accuracy,
                            formattedTime,
                            source,
                            formattedCreatedOn
                        )
                    )
                } while (cursor.moveToNext())
            }
        }
        return entities
    }

    private fun formatUnixTime(unixTime: Long): String {
        val formattedDateTime = DateTimeFormatter.ISO_INSTANT
            .format(Instant.ofEpochMilli(unixTime))
        return formattedDateTime
    }
}
