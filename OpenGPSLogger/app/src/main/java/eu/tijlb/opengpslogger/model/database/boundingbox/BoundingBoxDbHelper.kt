package eu.tijlb.opengpslogger.model.database.boundingbox

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import android.util.Log
import eu.tijlb.opengpslogger.model.dto.BBoxDto

class BoundingBoxDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val createTableSql =
        """
            CREATE TABLE IF NOT EXISTS ${BoundingBoxDbContract.TABLE_NAME}  
            (${BaseColumns._ID} INTEGER PRIMARY KEY,
            ${BoundingBoxDbContract.COLUMN_NAME_NAME} STRING UNIQUE,
            ${BoundingBoxDbContract.COLUMN_NAME_MIN_LATITUDE} DOUBLE,
            ${BoundingBoxDbContract.COLUMN_NAME_MAX_LATITUDE} DOUBLE,
            ${BoundingBoxDbContract.COLUMN_NAME_MIN_LONGITUDE} DOUBLE,
            ${BoundingBoxDbContract.COLUMN_NAME_MAX_LONGITUDE} DOUBLE,
            ${BoundingBoxDbContract.COLUMN_NAME_CREATED_ON} LONG
            )
        """.trimIndent()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(createTableSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

    }

    fun save(bbox: BBoxDto, name: String) {

        Log.d("ogl-boundingboxdbhelper", "Saving bounding box $name, $bbox")
        val db = this.writableDatabase

        val values = ContentValues().apply {
            put(
                BoundingBoxDbContract.COLUMN_NAME_NAME,
                name
            )
            put(
                BoundingBoxDbContract.COLUMN_NAME_MAX_LATITUDE,
                bbox.maxLat
            )
            put(
                BoundingBoxDbContract.COLUMN_NAME_MIN_LONGITUDE,
                bbox.minLon
            )
            put(
                BoundingBoxDbContract.COLUMN_NAME_MIN_LATITUDE,
                bbox.minLat
            )
            put(
                BoundingBoxDbContract.COLUMN_NAME_MAX_LONGITUDE,
                bbox.maxLon
            )
        }

        val newRowId =
            db?.replace(BoundingBoxDbContract.TABLE_NAME, null, values)
        Log.d("ogl-boundingboxdbhelper", "Saved $newRowId: $values to database")
    }

    fun get(name: String): Pair<Pair<Double, Double>, Pair<Double, Double>>? {
        val db = readableDatabase
        val query = """
            SELECT *
            FROM ${BoundingBoxDbContract.TABLE_NAME}
            WHERE ${BoundingBoxDbContract.COLUMN_NAME_NAME} = '$name'
        """.trimIndent()
        db.rawQuery(query, null)
            .use { cursor ->
                if (cursor.moveToFirst()) {
                    val minLat =
                        cursor.getDouble(cursor.getColumnIndexOrThrow(BoundingBoxDbContract.COLUMN_NAME_MIN_LATITUDE))
                    val maxLat =
                        cursor.getDouble(cursor.getColumnIndexOrThrow(BoundingBoxDbContract.COLUMN_NAME_MAX_LATITUDE))
                    val minLon =
                        cursor.getDouble(cursor.getColumnIndexOrThrow(BoundingBoxDbContract.COLUMN_NAME_MIN_LONGITUDE))
                    val maxLon =
                        cursor.getDouble(cursor.getColumnIndexOrThrow(BoundingBoxDbContract.COLUMN_NAME_MAX_LONGITUDE))
                    return Pair(Pair(maxLat, minLon), Pair(minLat, maxLon))
                }
            }
        return null
    }

    fun getNames(): List<String> {
        val query = """
            SELECT ${BoundingBoxDbContract.COLUMN_NAME_NAME} AS name
            FROM ${BoundingBoxDbContract.TABLE_NAME}
            ORDER BY ${BoundingBoxDbContract.COLUMN_NAME_NAME} ASC
        """.trimIndent()
        val db = readableDatabase
        db.rawQuery(query, null)
            .use { cursor ->
                val list = mutableListOf<String>()
                if (cursor.moveToFirst()) {
                    val sourceIndex = cursor.getColumnIndex("name")
                    do {
                        val source = cursor.getString(sourceIndex)
                        source?.let { list.add(it) }
                    } while (cursor.moveToNext())
                }
                Log.d("ogl-boundingboxdbhelper", "Got bounding box names $list")
                return list
            }
    }

    fun delete(name: String) {
        val whereClause = "${BoundingBoxDbContract.COLUMN_NAME_NAME} = ?"
        val db = writableDatabase
        db.delete(
            BoundingBoxDbContract.TABLE_NAME,
            whereClause,
            arrayOf(name)
        )
    }

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "boundingbox.sqlite"

        private var instance: BoundingBoxDbHelper? = null

        fun getInstance(context: Context): BoundingBoxDbHelper {
            return instance ?: synchronized(this) {
                instance ?: BoundingBoxDbHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
