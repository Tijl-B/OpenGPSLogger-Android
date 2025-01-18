package eu.tijlb.opengpslogger.database.location

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider

private const val MIME_TYPE_OCTET_STREAM = "application/octet-stream"

class LocationDatabaseFileProvider : FileProvider() {
    fun share(context: Context) {
        var dbPath = context.getDatabasePath(LocationDbContract.FILE_NAME)
        if (dbPath.exists()) {
            val uri = getUriForFile(
                context,
                "eu.tijlb.opengpslogger.provider",
                dbPath
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = MIME_TYPE_OCTET_STREAM
                putExtra(Intent.EXTRA_STREAM, uri)
            }

           context.startActivity(
                Intent.createChooser(shareIntent, "Share database file")
            )
        } else {
            Toast.makeText(context, "Database file not found", Toast.LENGTH_SHORT).show()
        }

    }
}