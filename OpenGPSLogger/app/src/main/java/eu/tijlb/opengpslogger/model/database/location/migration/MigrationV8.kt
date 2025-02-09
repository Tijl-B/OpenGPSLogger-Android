package eu.tijlb.opengpslogger.model.database.location.migration

import android.annotation.SuppressLint
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import eu.tijlb.opengpslogger.model.database.location.LocationDbContract
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper

object MigrationV8 {
    @SuppressLint("Range")
    fun migrate(db: SQLiteDatabase) {
        db.execSQL(
            """
                    ALTER TABLE ${LocationDbContract.TABLE_NAME}
                    ADD COLUMN ${LocationDbContract.COLUMN_NAME_HASH_250M_1D} LONG
                """.trimIndent()
        )
        // Populate hash_250m_1d for existing records
        val cursor = db.rawQuery(
            """
                    SELECT ${BaseColumns._ID}, ${LocationDbContract.COLUMN_NAME_LATITUDE}, 
                           ${LocationDbContract.COLUMN_NAME_LONGITUDE}, ${LocationDbContract.COLUMN_NAME_TIMESTAMP}
                    FROM ${LocationDbContract.TABLE_NAME}
                    """.trimIndent(), null
        )

        db.beginTransaction()
        try {
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getLong(cursor.getColumnIndex(BaseColumns._ID))
                    val latitude =
                        cursor.getDouble(cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_LATITUDE))
                    val longitude =
                        cursor.getDouble(cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_LONGITUDE))
                    val timestamp =
                        cursor.getLong(cursor.getColumnIndex(LocationDbContract.COLUMN_NAME_TIMESTAMP))

                    val hash =
                        LocationDbHelper.calculateHashes(latitude, longitude, timestamp).second

                    db.execSQL(
                        """
                            UPDATE ${LocationDbContract.TABLE_NAME} 
                            SET ${LocationDbContract.COLUMN_NAME_HASH_250M_1D} = $hash 
                            WHERE ${BaseColumns._ID} = $id
                            """
                    )
                } while (cursor.moveToNext())
            }
            db.execSQL(
                """
                            CREATE INDEX IF NOT EXISTS idx_hash_250m_1d 
                            ON ${LocationDbContract.TABLE_NAME} (${LocationDbContract.COLUMN_NAME_HASH_250M_1D})
                            """
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            cursor.close()
        }
    }
}