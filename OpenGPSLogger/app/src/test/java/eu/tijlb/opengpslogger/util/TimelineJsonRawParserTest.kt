package eu.tijlb.opengpslogger.util

import android.util.JsonReader
import eu.tijlb.opengpslogger.model.parser.json.TimelineJsonRawParser
import eu.tijlb.opengpslogger.ui.activity.ImportActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import java.io.StringReader


@RunWith(RobolectricTestRunner::class)
class TimelineJsonRawParserTest {

    private val saveMock: (ImportActivity.Point, Long, String) -> Unit = mock()

    @Test
    fun parseTimelineJson_withValidPositions() {
        val json = """
        [
            {
              "position": {
                "LatLng": "1.234°, -5.678°",
                "accuracyMeters": 100,
                "altitudeMeters": 980.2999877929688,
                "source": "WIFI",
                "timestamp": "2024-12-30T16:39:10.000-05:00",
                "speedMetersPerSecond": 0.1
              }
            },
            {
              "position": {
                "LatLng": "-0.122°, 4.567°",
                "accuracyMeters": 13,
                "altitudeMeters": 980.2999877929688,
                "source": "WIFI",
                "timestamp": "2024-12-30T16:44:25.000-05:00",
                "speedMetersPerSecond": 0.2
              }
            }
        ]
        """.trimIndent()

        val reader = JsonReader(StringReader(json))

        TimelineJsonRawParser.parse(reader, saveMock)

        verify(saveMock).invoke(
            eq(ImportActivity.Point(1.234, -5.678, 1735594750000, 0.1F, 100F)),
            any(),
            eq("timeline_json_import_raw")
        )
        verify(saveMock).invoke(
            eq(ImportActivity.Point(-0.122, 4.567, 1735595065000, 0.2F, 13F)),
            any(),
            eq("timeline_json_import_raw")
        )
    }
}
