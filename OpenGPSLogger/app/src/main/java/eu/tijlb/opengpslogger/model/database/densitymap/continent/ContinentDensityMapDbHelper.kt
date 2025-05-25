package eu.tijlb.opengpslogger.model.database.densitymap.continent

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.provider.BaseColumns
import android.util.Log
import kotlin.math.floor

class ContinentDensityMapDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val createTableSql =
        """
            CREATE TABLE IF NOT EXISTS ${ContinentDensityMapDbContract.TABLE_NAME}  
            (${BaseColumns._ID} INTEGER PRIMARY KEY,
            ${ContinentDensityMapDbContract.COLUMN_NAME_INDEX} LONG UNIQUE,
            ${ContinentDensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME} LONG,
            ${ContinentDensityMapDbContract.COLUMN_NAME_AMOUNT} LONG
            )
        """.trimIndent()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(createTableSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

    }

    fun addLocation(location: Location) {
        Log.d("ogl-densitymapdbhelper", "Saving location $location")
        val index = toIndex(location.latitude, location.longitude)
        val locationTime = location.time

        val db = this.writableDatabase
        addOrUpdateDensityPoint(db, index, locationTime)
        Log.d("ogl-densitymapdbhelper", "Saved $location to database")
    }

    fun addOrUpdateDensityPoint(
        db: SQLiteDatabase,
        index: Long,
        lastPointTime: Long
    ) {
        val updateSql = """
                UPDATE ${ContinentDensityMapDbContract.TABLE_NAME}
                SET ${ContinentDensityMapDbContract.COLUMN_NAME_AMOUNT} = ${ContinentDensityMapDbContract.COLUMN_NAME_AMOUNT} + 1,
                    ${ContinentDensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME} = ?
                WHERE ${ContinentDensityMapDbContract.COLUMN_NAME_INDEX} = ?
            """.trimIndent()
        val insertIfNoChanges = """
                INSERT INTO ${ContinentDensityMapDbContract.TABLE_NAME} (
                    ${ContinentDensityMapDbContract.COLUMN_NAME_INDEX},
                    ${ContinentDensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME},
                    ${ContinentDensityMapDbContract.COLUMN_NAME_AMOUNT}
                )
                SELECT ?, ?, 1
                WHERE (SELECT changes() = 0)
            """.trimIndent()

        db.beginTransaction()
        try {
            db.execSQL(updateSql, arrayOf(lastPointTime, index))
            db.execSQL(insertIfNoChanges, arrayOf(index, lastPointTime))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun toIndex(lat: Double, long: Double): Long {
        val amountOfSubdivisionsInWorld = 10000

        val xIndex = floor(((long + 180.0) / 360.0) * amountOfSubdivisionsInWorld).toLong()
        val yIndex = floor(((lat + 90.0) / 180.0) * amountOfSubdivisionsInWorld).toLong()

        val index = xIndex + amountOfSubdivisionsInWorld * yIndex
        return index
    }

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "densitymap_continent.sqlite"
    }
}
