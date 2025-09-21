package eu.tijlb.opengpslogger.model.parser.gpx

import android.util.Log
import eu.tijlb.opengpslogger.model.parser.ParserInterface
import eu.tijlb.opengpslogger.ui.fragment.ImportFragment
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private const val TAG = "ogl-zippedgpxparser"

private const val SUFFIX_GPX = ".gpx"
private const val SUFFIX_ZIP = ".zip"
private const val SUFFIX_GPX_GZ = ".gpx.gz"

object ZippedGpxParser : ParserInterface {
    override fun parse(
        inputStream: InputStream,
        save: (ImportFragment.Point, importStart: Long, source: String) -> Unit
    ) {
        try {
            extractZipStream(inputStream) { stream -> GpxParser.parse(stream, save) }
        } catch (e: Exception) {
            Log.e("ogl-importactivity-gpx", "Failed to parse GPX file: ${e.message}", e)
        }
    }

    private fun extractZipStream(input: InputStream, gpxParser: (InputStream) -> Unit) {
        ZipInputStream(input).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                Log.d(TAG, "Checking ${entry.name}")
                when {
                    entry.isDirectory -> {}
                    entry.name.endsWith(SUFFIX_GPX, ignoreCase = true) -> {
                        gpxParser(zip)
                    }

                    entry.name.endsWith(SUFFIX_ZIP, ignoreCase = true) -> {
                        val nestedZip = zip.readBytes()
                        extractZipStream(ByteArrayInputStream(nestedZip), gpxParser)
                    }

                    entry.name.endsWith(SUFFIX_GPX_GZ, ignoreCase = true) -> {
                        GZIPInputStream(ByteArrayInputStream(zip.readBytes()))
                            .use { gpx -> gpxParser(gpx) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}