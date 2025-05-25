package eu.tijlb.opengpslogger.database.density.continent

import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import eu.tijlb.opengpslogger.model.database.densitymap.continent.ContinentDensityMapDbContract
import eu.tijlb.opengpslogger.model.database.densitymap.continent.ContinentDensityMapDbHelper
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ContinentDensityMapDbHelperTest {

    private lateinit var dbHelper: ContinentDensityMapDbHelper
    private lateinit var tempDbFile: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        tempDbFile = File.createTempFile("test_densitymap_", ".sqlite")
        tempDbFile.deleteOnExit()

        dbHelper = ContinentDensityMapDbHelper(context)
        dbHelper.writableDatabase
    }

    @After
    fun tearDown() {
        dbHelper.close()
        tempDbFile.delete()
    }

    @Test
    fun insertSingleLocation() {
        val location = Location("test").apply {
            latitude = 50.0
            longitude = 8.0
            time = 111111L
        }

        dbHelper.addLocation(location)

        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ContinentDensityMapDbContract.TABLE_NAME,
            null, null, null, null, null, null
        )

        assertTrue(cursor.moveToFirst())
        assertEquals(
            1,
            cursor.getLong(cursor.getColumnIndexOrThrow(ContinentDensityMapDbContract.COLUMN_NAME_AMOUNT))
        )
        assertEquals(
            111111L,
            cursor.getLong(cursor.getColumnIndexOrThrow(ContinentDensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME))
        )
        cursor.close()
    }

    @Test
    fun addSameLocationTwice() {
        val location = Location("test").apply {
            latitude = 50.0
            longitude = 8.0
            time = 111111L
        }

        dbHelper.addLocation(location)

        val updatedLocation = Location("test").apply {
            latitude = 50.0
            longitude = 8.0
            time = 222222L
        }

        dbHelper.addLocation(updatedLocation)

        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ContinentDensityMapDbContract.TABLE_NAME,
            null,
            null, null, null, null, null
        )

        assertTrue(cursor.moveToFirst())
        assertEquals(
            2,
            cursor.getLong(cursor.getColumnIndexOrThrow(ContinentDensityMapDbContract.COLUMN_NAME_AMOUNT))
        )
        assertEquals(
            222222L,
            cursor.getLong(cursor.getColumnIndexOrThrow(ContinentDensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME))
        )
        cursor.close()
    }

    @Test
    fun addDifferentLocations() {
        val location = Location("test").apply {
            latitude = -50.0
            longitude = -8.0
            time = 111111L
        }

        dbHelper.addLocation(location)

        val updatedLocation = Location("test").apply {
            latitude = 50.0
            longitude = 8.0
            time = 222222L
        }

        dbHelper.addLocation(updatedLocation)

        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ContinentDensityMapDbContract.TABLE_NAME,
            null,
            null, null, null, null, null
        )

        assertTrue(cursor.moveToFirst())
        assertEquals(
            1,
            cursor.getLong(cursor.getColumnIndexOrThrow(ContinentDensityMapDbContract.COLUMN_NAME_AMOUNT))
        )
        assertEquals(
            111111L,
            cursor.getLong(cursor.getColumnIndexOrThrow(ContinentDensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME))
        )
        assertTrue(cursor.moveToNext())

        assertEquals(
            1,
            cursor.getLong(cursor.getColumnIndexOrThrow(ContinentDensityMapDbContract.COLUMN_NAME_AMOUNT))
        )
        assertEquals(
            222222L,
            cursor.getLong(cursor.getColumnIndexOrThrow(ContinentDensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME))
        )
        cursor.close()
    }
}
