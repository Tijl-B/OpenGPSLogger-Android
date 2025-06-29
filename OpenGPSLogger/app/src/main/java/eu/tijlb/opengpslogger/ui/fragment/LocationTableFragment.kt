package eu.tijlb.opengpslogger.ui.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.location.Location
import android.os.Bundle
import android.provider.BaseColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import eu.tijlb.opengpslogger.R
import eu.tijlb.opengpslogger.databinding.FragmentDatabaseBinding
import eu.tijlb.opengpslogger.model.broadcast.LocationUpdateReceiver
import eu.tijlb.opengpslogger.model.database.location.LocationDatabaseFileProvider
import eu.tijlb.opengpslogger.model.database.location.LocationDbContract
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter

class LocationTableFragment : Fragment() {

    lateinit var tableLayout: TableLayout

    private var _binding: FragmentDatabaseBinding? = null
    private lateinit var locationDatabaseFileProvider: LocationDatabaseFileProvider
    private lateinit var locationReceiver: LocationUpdateReceiver

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentDatabaseBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tableLayout = view.findViewById(R.id.table_recent_locations)
        locationDatabaseFileProvider = LocationDatabaseFileProvider()

        binding.buttonShare.setOnClickListener {
            locationDatabaseFileProvider.share(requireContext())
        }

        locationReceiver = LocationUpdateReceiver().apply {
            setOnLocationReceivedListener { location ->
                addNewLocation(location)
            }
        }

        val filter = IntentFilter("eu.tijlb.LOCATION_UPDATE")
        requireContext().registerReceiver(locationReceiver, filter, Context.RECEIVER_EXPORTED)

        viewLifecycleOwner.lifecycleScope.launch {
            val tableRows = withContext(Dispatchers.IO) {
                val entities = getLastEntities()
                Log.d("ogl-locationtablefragment", "Got ${entities.size} entities")
                toTableRows(entities)
            }
            populateTable(tableRows)
        }
    }


    private fun addNewLocation(location: Location) {
        context?.let { c ->
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
                var tableRow = buildTableRow(row, c)
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
        return (listOf(columnTitles) + entities + listOf(tail)).map {
            buildTableRow(it, requireContext())
        }
    }

    private fun populateTable(tableRows: List<TableRow>) {
        tableRows.forEach { tableLayout.addView(it) }
    }

    private fun buildTableRow(rowData: List<String>, context: Context): TableRow {
        val tableRow = TableRow(context)

        for (columnData in rowData) {
            val textView = TextView(context)
            textView.text = columnData
            textView.setPadding(16, 16, 16, 16)

            tableRow.addView(textView)
        }
        return tableRow
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        requireContext().unregisterReceiver(locationReceiver)
    }

    @SuppressLint("Range")
    private fun getLastEntities(): List<List<String>> {
        val dbHelper = LocationDbHelper.getInstance(requireContext())
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
                val accuracyIndex = cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_ACCURACY)
                val timestampIndex = cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_TIMESTAMP)
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
