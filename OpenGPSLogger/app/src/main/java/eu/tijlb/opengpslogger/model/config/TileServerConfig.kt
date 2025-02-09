package eu.tijlb.opengpslogger.model.config

import android.content.Context
import org.json.JSONObject

object TileServerConfig {
    private const val CONFIG_FILE_NAME = "tileservers"
    private const val JSON_SERVERS = "servers"

    fun getTileServers(context: Context): List<Triple<String, String, String>> {
        val fileText = ConfigLoader.readConfig(context, CONFIG_FILE_NAME)
        return parseTileServerConfig(fileText)
    }

    private fun parseTileServerConfig(jsonString: String): List<Triple<String, String, String>> {
        val jsonObject = JSONObject(jsonString)
        val serversArray = jsonObject.getJSONArray(JSON_SERVERS)
        val serversList = mutableListOf<Triple<String, String, String>>()

        for (i in 0 until serversArray.length()) {
            val server = serversArray.getJSONArray(i)
            val element = Triple(server.getString(0), server.getString(1), server.getString(2))
            serversList.add(element)
        }

        return serversList
    }
}
