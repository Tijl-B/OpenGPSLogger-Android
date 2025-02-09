package eu.tijlb.opengpslogger.util

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.FileNotFoundException

object ConfigLoaderUtil {
    fun getTileServers(context: Context): List<Triple<String, String, String>> {
        val name = "tileservers"
        val configFileName =
            if (fileExists(context, "$name.override.json")) "$name.override.json" else "$name.json"
        val jsonString = context.assets
            .open(configFileName)
            .bufferedReader()
            .use { it.readText() }
        return parseTileServerConfig(jsonString)
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

    private fun parseTileServerConfig(jsonString: String): List<Triple<String, String, String>> {
        val jsonObject = JSONObject(jsonString)
        val serversArray = jsonObject.getJSONArray("servers")
        val serversList = mutableListOf<Triple<String, String, String>>()

        for (i in 0 until serversArray.length()) {
            val server = serversArray.getJSONArray(i)
            val element = Triple(server.getString(0), server.getString(1), server.getString(2))
            serversList.add(element)
        }

        return serversList
    }
}
