package eu.tijlb.opengpslogger.model.database.location.migration

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import eu.tijlb.opengpslogger.model.database.location.LocationDbContract
import eu.tijlb.opengpslogger.model.database.location.LocationDbMigrationContract

class MigrationV9(val context: Context) {
    @SuppressLint("Range")
    fun migrate(db: SQLiteDatabase) {
        db.beginTransaction()

        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ${LocationDbMigrationContract.TABLE_NAME} (
                    ${LocationDbMigrationContract.COLUMN_NAME_VERSION} INTEGER NOT NULL,
                    ${LocationDbMigrationContract.COLUMN_NAME_EXECUTED_ON} INTEGER
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                    ALTER TABLE ${LocationDbContract.TABLE_NAME}
                        ADD COLUMN  ${LocationDbContract.COLUMN_NAME_NEIGHBOR_DISTANCE} DOUBLE
                """.trimIndent()
            )

            db.execSQL(
                """
                    ALTER TABLE ${LocationDbContract.TABLE_NAME}
                        ADD COLUMN ${LocationDbContract.COLUMN_NAME_NEIGHBOR_ANGLE} DOUBLE
                """.trimIndent()
            )

            db.execSQL(
                """
                    CREATE INDEX IF NOT EXISTS idx_hash_neighbor_distance 
                    ON ${LocationDbContract.TABLE_NAME} (${LocationDbContract.COLUMN_NAME_NEIGHBOR_DISTANCE})
                    """
            )
            db.execSQL(
                """
                    CREATE INDEX IF NOT EXISTS idx_hash_neighbor_angle
                    ON ${LocationDbContract.TABLE_NAME} (${LocationDbContract.COLUMN_NAME_NEIGHBOR_ANGLE})
                    """
            )

            val currentTimeMillis = System.currentTimeMillis()
            db.execSQL(
                """
                INSERT INTO ${LocationDbMigrationContract.TABLE_NAME} (
                    ${LocationDbMigrationContract.COLUMN_NAME_VERSION}, 
                    ${LocationDbMigrationContract.COLUMN_NAME_EXECUTED_ON}
                ) VALUES (
                    9, 
                    $currentTimeMillis
                )
                """.trimIndent()
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        val workRequest = OneTimeWorkRequestBuilder<MigrationV9Worker>()
            .addTag("MigrationV9Worker")
            .build()

        WorkManager.getInstance(context.applicationContext).enqueue(workRequest)
    }
}