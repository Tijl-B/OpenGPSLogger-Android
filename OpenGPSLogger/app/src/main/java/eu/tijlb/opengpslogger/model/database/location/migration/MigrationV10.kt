package eu.tijlb.opengpslogger.model.database.location.migration

import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction

object MigrationV10 {
    fun migrate(db: SQLiteDatabase) {
        db.transaction() {
            execSQL("DROP INDEX IF EXISTS idx_hash_250m_1d;")
            execSQL("DROP INDEX IF EXISTS idx_hash_50m_1d;")
            execSQL("DROP INDEX IF EXISTS idx_hash_neighbor_angle;")
            execSQL("DROP INDEX IF EXISTS idx_hash_neighbor_distance;")
            execSQL("DROP INDEX IF EXISTS idx_hash_outlier;")
            execSQL("DROP INDEX IF EXISTS idx_unique_timestamp;")
            execSQL("DROP INDEX IF EXISTS idx_unique_timestamp_source;")
        }
    }
}