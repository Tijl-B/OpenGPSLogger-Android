package eu.tijlb.opengpslogger.model.config

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.FileNotFoundException

object ConfigLoader {
    fun readConfig(context: Context, name: String): String {
        val configFileName =
            if (fileExists(context, "$name.override.json")) "$name.override.json" else "$name.json"
        val fileText = context.assets
            .open(configFileName)
            .bufferedReader()
            .use { it.readText() }
        return fileText
    }

    private fun fileExists(context: Context, fileName: String): Boolean {
        return try {
            context.assets.open(fileName).close()
            true
        } catch (e: FileNotFoundException) {
            Log.d("ogl-configloaderutil", "File $fileName does not exist", e)
            false
        }
    }
}
