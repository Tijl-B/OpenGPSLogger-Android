package eu.tijlb.opengpslogger.database.density.continent

import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import eu.tijlb.opengpslogger.model.database.densitymap.continent.ContinentDensityMapDbHelper
import eu.tijlb.opengpslogger.model.database.densitymap.continent.DensityMapDbContract
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
            DensityMapDbContract.TABLE_NAME,
            null, null, null, null, null, null
        )

        assertTrue(cursor.moveToFirst())
        assertEquals(
            1,
            cursor.getLong(cursor.getColumnIndexOrThrow(DensityMapDbContract.COLUMN_NAME_AMOUNT))
        )
        assertEquals(
            111111L,
            cursor.getLong(cursor.getColumnIndexOrThrow(DensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME))
        )
        cursor.close()
    }

    @Test
    fun addSameLocationTwiceWithLotsOfTimeInBetween() {
        val location = Location("test").apply {
            latitude = 50.0
            longitude = 8.0
            time = 1L
        }

        dbHelper.addLocation(location)

        val updatedLocation = Location("test").apply {
            latitude = 50.0
            longitude = 8.0
            time = 222222222L
        }

        dbHelper.addLocation(updatedLocation)

        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DensityMapDbContract.TABLE_NAME,
            null,
            null, null, null, null, null
        )

        assertTrue(cursor.moveToFirst())
        assertEquals(
            2,
            cursor.getLong(cursor.getColumnIndexOrThrow(DensityMapDbContract.COLUMN_NAME_AMOUNT))
        )
        assertEquals(
            222222222L,
            cursor.getLong(cursor.getColumnIndexOrThrow(DensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME))
        )
        cursor.close()
    }

    @Test
    fun addSameLocationTwiceWithLittleTimeInBetween() {
        val location = Location("test").apply {
            latitude = 50.0
            longitude = 8.0
            time = 1L
        }

        dbHelper.addLocation(location)

        val updatedLocation = Location("test").apply {
            latitude = 50.0
            longitude = 8.0
            time = 2L
        }

        dbHelper.addLocation(updatedLocation)

        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DensityMapDbContract.TABLE_NAME,
            null,
            null, null, null, null, null
        )

        assertTrue(cursor.moveToFirst())
        assertEquals(
            1,
            cursor.getLong(cursor.getColumnIndexOrThrow(DensityMapDbContract.COLUMN_NAME_AMOUNT))
        )
        assertEquals(
            1L,
            cursor.getLong(cursor.getColumnIndexOrThrow(DensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME))
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
            DensityMapDbContract.TABLE_NAME,
            null,
            null, null, null, null, null
        )

        assertTrue(cursor.moveToFirst())
        assertEquals(
            1,
            cursor.getLong(cursor.getColumnIndexOrThrow(DensityMapDbContract.COLUMN_NAME_AMOUNT))
        )
        assertEquals(
            111111L,
            cursor.getLong(cursor.getColumnIndexOrThrow(DensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME))
        )
        assertTrue(cursor.moveToNext())

        assertEquals(
            1,
            cursor.getLong(cursor.getColumnIndexOrThrow(DensityMapDbContract.COLUMN_NAME_AMOUNT))
        )
        assertEquals(
            222222L,
            cursor.getLong(cursor.getColumnIndexOrThrow(DensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME))
        )
        cursor.close()
    }
}
