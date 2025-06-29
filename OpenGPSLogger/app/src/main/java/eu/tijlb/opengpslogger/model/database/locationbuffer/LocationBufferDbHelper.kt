package eu.tijlb.opengpslogger.model.database.locationbuffer

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.provider.BaseColumns
import android.util.Log

class LocationBufferDbHelper(val context: Context) :
    SQLiteOpenHelper(context, LocationBufferDbContract.FILE_NAME, null, DATABASE_VERSION) {

    private val createTableSql =
        """
            CREATE TABLE IF NOT EXISTS ${LocationBufferDbContract.TABLE_NAME}  
            (${BaseColumns._ID} INTEGER PRIMARY KEY,
            ${LocationBufferDbContract.COLUMN_NAME_TIMESTAMP} INTEGER,
            ${LocationBufferDbContract.COLUMN_NAME_LATITUDE} DOUBLE,
            ${LocationBufferDbContract.COLUMN_NAME_LONGITUDE} DOUBLE,
            ${LocationBufferDbContract.COLUMN_NAME_SPEED} DOUBLE,
            ${LocationBufferDbContract.COLUMN_NAME_SPEED_ACCURACY} DOUBLE,
            ${LocationBufferDbContract.COLUMN_NAME_ACCURACY} DOUBLE,
            ${LocationBufferDbContract.COLUMN_NAME_SOURCE} TEXT
           )
        """.trimIndent()


    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(createTableSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.execSQL("PRAGMA synchronous = NORMAL")
    }

    fun save(location: Location, source: String): Long {
        val values = ContentValues().apply {
            val speed = if (location.hasSpeed()) location.speed else null
            val speedAccuracy =
                if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else null
            val accuracy = if (location.hasAccuracy()) location.accuracy else null

            put(LocationBufferDbContract.COLUMN_NAME_TIMESTAMP, location.time)
            put(LocationBufferDbContract.COLUMN_NAME_LATITUDE, location.latitude)
            put(LocationBufferDbContract.COLUMN_NAME_LONGITUDE, location.longitude)
            put(LocationBufferDbContract.COLUMN_NAME_SPEED, speed)
            put(LocationBufferDbContract.COLUMN_NAME_SPEED_ACCURACY, speedAccuracy)
            put(LocationBufferDbContract.COLUMN_NAME_ACCURACY, accuracy)
            put(LocationBufferDbContract.COLUMN_NAME_SOURCE, source)
        }

        val newRowId = writableDatabase.insert(LocationBufferDbContract.TABLE_NAME, null, values)
        if (newRowId % 1000 == 0L) {
            Log.d("ogl-locationdbhelper", "Saved $newRowId: $values to database")
        }
        return newRowId
    }

    fun getPointsCursor(): Cursor {
        return readableDatabase.query(
            LocationBufferDbContract.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "${BaseColumns._ID} ASC"
        )
    }

    fun remove(index: Int) {
        writableDatabase.delete(
            LocationBufferDbContract.TABLE_NAME,
            "${BaseColumns._ID} = ?",
            arrayOf(index.toString())
        )
    }


    companion object {
        private const val DATABASE_VERSION = 1 // Increment on schema change

        private var instance: LocationBufferDbHelper? = null

        fun getInstance(context: Context): LocationBufferDbHelper {
            return instance ?: synchronized(this) {
                instance ?: LocationBufferDbHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}