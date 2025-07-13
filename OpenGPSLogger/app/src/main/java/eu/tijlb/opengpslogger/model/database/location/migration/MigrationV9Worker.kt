package eu.tijlb.opengpslogger.model.database.location.migration

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import eu.tijlb.opengpslogger.model.database.location.LocationDbHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MigrationV9Worker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        Log.d(
            "ogl-migrationv9worker",
            "Starting back filling existing rows with angle and distance."
        )

        CoroutineScope(Dispatchers.IO).launch {
            LocationDbHelper(applicationContext)
                .updateDistAngleIfNeeded()

            Log.d(
                "ogl-migrationv9worker",
                "Done back filling existing rows with angle and distance."
            )
        }
        return Result.success()
    }
}
