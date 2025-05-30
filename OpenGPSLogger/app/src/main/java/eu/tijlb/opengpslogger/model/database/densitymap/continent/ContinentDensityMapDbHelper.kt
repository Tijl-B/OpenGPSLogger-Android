package eu.tijlb.opengpslogger.model.database.densitymap.continent

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.provider.BaseColumns
import android.util.Log
import kotlin.math.ln
import kotlin.math.tan

const val SUBDIVISIONS_CONTINENT = 25_000
class ContinentDensityMapDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val createTableSql =
        """
            CREATE TABLE IF NOT EXISTS ${ContinentDensityMapDbContract.TABLE_NAME}  
            (${BaseColumns._ID} INTEGER PRIMARY KEY,
            ${ContinentDensityMapDbContract.COLUMN_NAME_X_INDEX} LONG,
            ${ContinentDensityMapDbContract.COLUMN_NAME_Y_INDEX} LONG,
            ${ContinentDensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME} LONG,
            ${ContinentDensityMapDbContract.COLUMN_NAME_AMOUNT} LONG,
            UNIQUE(${ContinentDensityMapDbContract.COLUMN_NAME_X_INDEX}, ${ContinentDensityMapDbContract.COLUMN_NAME_Y_INDEX})
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
        val (xIndex, yIndex) = toIndex(location.latitude, location.longitude)
        val locationTime = location.time

        val db = this.writableDatabase
        addOrUpdateDensityPoint(db, xIndex, yIndex, locationTime)
        Log.d("ogl-densitymapdbhelper", "Saved $location to database")
    }

    fun addPoint(lat: Double, long: Double, time: Long) {
        val (xIndex, yIndex) = toIndex(lat, long)
        val db = this.writableDatabase
        addOrUpdateDensityPoint(db, xIndex, yIndex, time)
    }


    private fun addOrUpdateDensityPoint(
        db: SQLiteDatabase,
        xIndex: Long,
        yIndex: Long,
        lastPointTime: Long
    ) {
        val updateSql = """
            UPDATE ${ContinentDensityMapDbContract.TABLE_NAME}
            SET ${ContinentDensityMapDbContract.COLUMN_NAME_AMOUNT} = ${ContinentDensityMapDbContract.COLUMN_NAME_AMOUNT} + 1,
                ${ContinentDensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME} = ?
            WHERE ${ContinentDensityMapDbContract.COLUMN_NAME_X_INDEX} = ?
              AND ${ContinentDensityMapDbContract.COLUMN_NAME_Y_INDEX} = ?
              AND (? - ${ContinentDensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME}) > 900000
        """.trimIndent()

        val insertIfNoChanges = """
            INSERT OR IGNORE INTO ${ContinentDensityMapDbContract.TABLE_NAME} (
                ${ContinentDensityMapDbContract.COLUMN_NAME_X_INDEX},
                ${ContinentDensityMapDbContract.COLUMN_NAME_Y_INDEX},
                ${ContinentDensityMapDbContract.COLUMN_NAME_LAST_POINT_TIME},
                ${ContinentDensityMapDbContract.COLUMN_NAME_AMOUNT}
            ) 
            VALUES (?, ?, ?, 1)
        """.trimIndent()

        db.beginTransaction()
        try {
            db.execSQL(updateSql, arrayOf(lastPointTime, xIndex, yIndex, lastPointTime))
            db.execSQL(insertIfNoChanges, arrayOf(xIndex, yIndex, lastPointTime))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun toIndex(lat: Double, long: Double): Pair<Long, Long> {
        val maxLat = 85.05112878
        val clampedLat = lat.coerceIn(-maxLat, maxLat)
        val xIndex = ((long + 180.0) / 360.0 * SUBDIVISIONS_CONTINENT).toLong()
        val latRad = Math.toRadians(clampedLat)
        val yMerc = (1.0 - ln(tan(Math.PI / 4 + latRad / 2)) / Math.PI) / 2.0
        val yIndex = (yMerc * SUBDIVISIONS_CONTINENT).toLong()

        return Pair(xIndex, yIndex)
    }

    fun drop() {
        val db = this.writableDatabase
        db.execSQL("DELETE FROM ${ContinentDensityMapDbContract.TABLE_NAME}")
    }

    fun getAllPoints(): Cursor {
        return readableDatabase.query(
            ContinentDensityMapDbContract.TABLE_NAME,
            null, null, null, null, null, null
        )
    }

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "densitymap_continent.sqlite"

        private var instance: ContinentDensityMapDbHelper? = null

        fun getInstance(context: Context): ContinentDensityMapDbHelper {
            return instance ?: synchronized(this) {
                instance ?: ContinentDensityMapDbHelper(context).also { instance = it }
            }
        }
    }
}
