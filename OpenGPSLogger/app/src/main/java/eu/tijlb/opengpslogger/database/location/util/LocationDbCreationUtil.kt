package eu.tijlb.opengpslogger.database.location.util

import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import eu.tijlb.opengpslogger.database.location.LocationDbContract
import eu.tijlb.opengpslogger.database.location.LocationDbMigrationContract

class LocationDbCreationUtil {
    companion object {
        private val createTableSql =
            """
            CREATE TABLE IF NOT EXISTS ${LocationDbContract.TABLE_NAME}  
            (${BaseColumns._ID} INTEGER PRIMARY KEY,
            ${LocationDbContract.COLUMN_NAME_TIMESTAMP} INTEGER,
            ${LocationDbContract.COLUMN_NAME_LATITUDE} DOUBLE,
            ${LocationDbContract.COLUMN_NAME_LONGITUDE} DOUBLE,
            ${LocationDbContract.COLUMN_NAME_SPEED} DOUBLE,
            ${LocationDbContract.COLUMN_NAME_SPEED_ACCURACY} DOUBLE,
            ${LocationDbContract.COLUMN_NAME_ACCURACY} DOUBLE,
            ${LocationDbContract.COLUMN_NAME_SOURCE} TEXT,
            ${LocationDbContract.COLUMN_NAME_CREATED_ON} INTEGER,
            ${LocationDbContract.COLUMN_NAME_HASH_50M_1D} LONG,
            ${LocationDbContract.COLUMN_NAME_HASH_250M_1D} LONG,
            ${LocationDbContract.COLUMN_NAME_NEIGHBOR_DISTANCE} DOUBLE,
            ${LocationDbContract.COLUMN_NAME_NEIGHBOR_ANGLE} DOUBLE,
            UNIQUE(${LocationDbContract.COLUMN_NAME_TIMESTAMP}, ${LocationDbContract.COLUMN_NAME_SOURCE})
            )
        """.trimIndent()

        fun create(db: SQLiteDatabase, version: Int) {
            db.execSQL(createTableSql)
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS ${LocationDbMigrationContract.TABLE_NAME} (
                ${LocationDbMigrationContract.COLUMN_NAME_VERSION} INTEGER NOT NULL,
                ${LocationDbMigrationContract.COLUMN_NAME_EXECUTED_ON} INTEGER
            )
            """.trimIndent()
            )
            createIndexes(db)

            db.execSQL(
                """
                INSERT INTO ${LocationDbMigrationContract.TABLE_NAME} (
                    ${LocationDbMigrationContract.COLUMN_NAME_VERSION}, 
                    ${LocationDbMigrationContract.COLUMN_NAME_EXECUTED_ON}
                ) VALUES (
                    $version, 
                    null
                )
                """.trimIndent()
            )
        }

        private fun createIndexes(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_timestamp 
                ON ${LocationDbContract.TABLE_NAME} (${LocationDbContract.COLUMN_NAME_TIMESTAMP})
                """
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_latitude_longitude 
                ON ${LocationDbContract.TABLE_NAME} (${LocationDbContract.COLUMN_NAME_LATITUDE}, ${LocationDbContract.COLUMN_NAME_LONGITUDE})
                """
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_data_source 
                ON ${LocationDbContract.TABLE_NAME} (${LocationDbContract.COLUMN_NAME_SOURCE})
                """
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_hash_50m_1d 
                ON ${LocationDbContract.TABLE_NAME} (${LocationDbContract.COLUMN_NAME_HASH_50M_1D})
                """
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_hash_250m_1d 
                ON ${LocationDbContract.TABLE_NAME} (${LocationDbContract.COLUMN_NAME_HASH_250M_1D})
                """
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
        }
    }
}