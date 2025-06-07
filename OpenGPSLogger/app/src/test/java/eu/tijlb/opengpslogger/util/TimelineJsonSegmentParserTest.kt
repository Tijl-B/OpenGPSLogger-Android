package eu.tijlb.opengpslogger.util

import android.util.JsonReader
import eu.tijlb.opengpslogger.model.parser.json.TimelineJsonSegmentParser
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
class TimelineJsonSegmentParserTest {

    private val saveMock: (ImportActivity.Point, Long, String) -> Unit = mock()

    @Test
    fun parseTimelineJson_withValidPositions() {
        val json = """
        [
            {
              "startTime": "2023-06-02T02:45:37.000-05:00",
              "endTime": "2023-06-02T03:03:57.000-05:00",
              "activity": {
                "start": {
                  "latLng": "1.234°, 2.345°"
                },
                "end": {
                  "latLng": "3.456°, 4.567°"
                },
                "distanceMeters": 5880.0,
                "topCandidate": {
                  "type": "IN_PASSENGER_VEHICLE",
                  "probability": 0.0
                }
              }
            },
            {
              "startTime": "2023-06-02T03:00:00.000-05:00",
              "endTime": "2023-06-02T05:00:00.000-05:00",
              "timelinePath": [
                {
                  "point": "5.678°, 6.789°",
                  "time": "2023-06-02T03:05:00.000-05:00"
                },
                {
                  "point": "7.890°, 8.901°",
                  "time": "2023-06-02T03:07:00.000-05:00"
                }
              ]
            },
            {
              "startTime": "2023-06-02T03:03:57.000-05:00",
              "endTime": "2023-06-02T04:46:20.000-05:00",
              "startTimeTimezoneUtcOffsetMinutes": 120,
              "endTimeTimezoneUtcOffsetMinutes": 120,
              "visit": {
                "hierarchyLevel": 0,
                "probability": 0.9399999976158142,
                "topCandidate": {
                  "placeId": "ChIJSe2gtRZhwUcR5YJW5sQ8ha4",
                  "semanticType": "INFERRED_WORK",
                  "probability": 0.13997095823287964,
                  "placeLocation": {
                    "latLng": "9.012°, 0.123°"
                  }
                }
              }
            }
        ]
        """.trimIndent()

        val reader = JsonReader(StringReader(json))

        TimelineJsonSegmentParser.parse(reader, saveMock)

        verify(saveMock).invoke(
            eq(ImportActivity.Point(1.234, 2.345, 1685691937000)),
            any(),
            eq("timeline_json_import_segment")
        )
        verify(saveMock).invoke(
            eq(ImportActivity.Point(3.456, 4.567, 1685693037000)),
            any(),
            eq("timeline_json_import_segment")
        )

        // Verifying that timeline points were saved
        verify(saveMock).invoke(
            eq(ImportActivity.Point(5.678, 6.789, 1685693100000)),
            any(),
            eq("timeline_json_import_segment")
        )
        verify(saveMock).invoke(
            eq(ImportActivity.Point(7.890, 8.901, 1685693220000)),
            any(),
            eq("timeline_json_import_segment")
        )
        verify(saveMock).invoke(
            eq(ImportActivity.Point(9.012, 0.123, 1685699180000)),
            any(),
            eq("timeline_json_import_segment")
        )
    }
}
