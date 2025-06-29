package eu.tijlb.opengpslogger.model.database.tileserver

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import android.util.Log
import eu.tijlb.opengpslogger.model.config.TileServerConfig

class TileServerDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val createTableSql =
        """
        CREATE TABLE IF NOT EXISTS ${TileServerDbContract.TABLE_NAME}  
        (
            ${BaseColumns._ID} INTEGER PRIMARY KEY,
            ${TileServerDbContract.COLUMN_NAME_NAME} TEXT NOT NULL UNIQUE,
            ${TileServerDbContract.COLUMN_NAME_URL} TEXT NOT NULL,
            ${TileServerDbContract.COLUMN_NAME_COPYRIGHT_NOTICE} TEXT NOT NULL DEFAULT '',
            ${TileServerDbContract.COLUMN_NAME_SELECTED} BOOLEAN NOT NULL CHECK (${TileServerDbContract.COLUMN_NAME_SELECTED} IN (0,1))
        );
        """.trimIndent()

    private val maxOneSelectedSql =
        """
        CREATE UNIQUE INDEX unique_selected_true 
        ON ${TileServerDbContract.TABLE_NAME} (${TileServerDbContract.COLUMN_NAME_SELECTED}) 
        WHERE ${TileServerDbContract.COLUMN_NAME_SELECTED} = 1;
        """.trimIndent()

    private val defaultServers = TileServerConfig.getTileServers(context)

    private val insertDefaultServersSql =
        """
        INSERT OR REPLACE INTO ${TileServerDbContract.TABLE_NAME} 
        (${TileServerDbContract.COLUMN_NAME_NAME}, ${TileServerDbContract.COLUMN_NAME_URL}, ${TileServerDbContract.COLUMN_NAME_COPYRIGHT_NOTICE}, ${TileServerDbContract.COLUMN_NAME_SELECTED}) 
        VALUES ${defaultServers.joinToString(", ") { "('${it.first}', '${it.second}', '${it.third}', 0)" }};
        """.trimIndent()


    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(createTableSql)
        db.execSQL(maxOneSelectedSql)
        db.execSQL(insertDefaultServersSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                    ALTER TABLE ${TileServerDbContract.TABLE_NAME}
                    ADD COLUMN ${TileServerDbContract.COLUMN_NAME_COPYRIGHT_NOTICE} TEXT NOT NULL DEFAULT ''
                """.trimIndent()
            )
            db.execSQL(insertDefaultServersSql)
        }
    }

    fun save(name: String, url: String, copyright: String) {
        Log.d("ogl-tileserverdbhelper", "Saving tile server $name, $url")
        val db = this.writableDatabase

        val values = ContentValues().apply {
            put(TileServerDbContract.COLUMN_NAME_NAME, name)
            put(TileServerDbContract.COLUMN_NAME_URL, url)
            put(TileServerDbContract.COLUMN_NAME_COPYRIGHT_NOTICE, copyright)
            put(TileServerDbContract.COLUMN_NAME_SELECTED, false)
        }

        val newRowId =
            db?.replace(TileServerDbContract.TABLE_NAME, null, values)
        Log.d("ogl-tileserverdbhelper", "Saved $newRowId: $values to database")
    }

    fun get(name: String): String? {
        val db = readableDatabase
        val query = """
            SELECT *
            FROM ${TileServerDbContract.TABLE_NAME}
            WHERE ${TileServerDbContract.COLUMN_NAME_NAME} = '$name'
        """.trimIndent()
        db.rawQuery(query, null)
            .use { cursor ->
                if (cursor.moveToFirst()) {
                    val url =
                        cursor.getString(cursor.getColumnIndexOrThrow(TileServerDbContract.COLUMN_NAME_URL))
                    return url
                }
            }
        return null
    }

    fun getNames(): List<String> {
        val query = """
            SELECT ${TileServerDbContract.COLUMN_NAME_NAME} AS name
            FROM ${TileServerDbContract.TABLE_NAME}
            ORDER BY ${TileServerDbContract.COLUMN_NAME_SELECTED} DESC,
                ${TileServerDbContract.COLUMN_NAME_NAME} ASC
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
                Log.d("ogl-tileserverdbhelper", "Got tile server names $list")
                return list
            }
    }

    fun setActive(name: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val stmt1 = db.compileStatement(
                """
                UPDATE ${TileServerDbContract.TABLE_NAME}
                SET ${TileServerDbContract.COLUMN_NAME_SELECTED} = 0;
                """.trimIndent()
            )
            stmt1.executeUpdateDelete()

            val stmt2 = db.compileStatement(
                """
                UPDATE ${TileServerDbContract.TABLE_NAME}
                SET ${TileServerDbContract.COLUMN_NAME_SELECTED} = 1
                WHERE ${TileServerDbContract.COLUMN_NAME_NAME} = ?;
                """.trimIndent()
            )
            stmt2.bindString(1, name)
            stmt2.executeUpdateDelete()

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun delete(name: String) {
        val whereClause = "${TileServerDbContract.COLUMN_NAME_NAME} = ?"
        val db = writableDatabase
        db.delete(
            TileServerDbContract.TABLE_NAME,
            whereClause,
            arrayOf(name)
        )
    }

    fun getSelectedUrl(): String {
        val query = """
            SELECT ${TileServerDbContract.COLUMN_NAME_URL} AS url
            FROM ${TileServerDbContract.TABLE_NAME}
            ORDER BY ${TileServerDbContract.COLUMN_NAME_SELECTED} DESC,
                ${TileServerDbContract.COLUMN_NAME_NAME} ASC
            LIMIT 1
        """.trimIndent()

        val db = readableDatabase
        db.rawQuery(query, null).use { cursor ->
            return if (cursor.moveToFirst()) {
                val sourceIndex = cursor.getColumnIndex("url")
                cursor.getString(sourceIndex)
            } else {
                defaultServers[0].second
            }
        }

    }

    fun getSelectedCopyrightNotice(): String {
        val query = """
            SELECT ${TileServerDbContract.COLUMN_NAME_COPYRIGHT_NOTICE}
            FROM ${TileServerDbContract.TABLE_NAME}
            ORDER BY ${TileServerDbContract.COLUMN_NAME_SELECTED} DESC,
                ${TileServerDbContract.COLUMN_NAME_NAME} ASC
            LIMIT 1
        """.trimIndent()

        val db = readableDatabase
        db.rawQuery(query, null).use { cursor ->
            return if (cursor.moveToFirst()) {
                val sourceIndex =
                    cursor.getColumnIndex(TileServerDbContract.COLUMN_NAME_COPYRIGHT_NOTICE)
                cursor.getString(sourceIndex)
            } else {
                defaultServers[0].third
            }
        }

    }

    companion object {
        const val DATABASE_VERSION = 2
        const val DATABASE_NAME = "tileserver.sqlite"

        private var instance: TileServerDbHelper? = null

        fun getInstance(context: Context): TileServerDbHelper {
            return instance ?: synchronized(this) {
                instance ?: TileServerDbHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
