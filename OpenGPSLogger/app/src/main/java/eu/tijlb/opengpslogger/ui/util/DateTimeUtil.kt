package eu.tijlb.opengpslogger.ui.util

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.util.Log
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar

object DateTimeUtil {
    private const val TAG = "ogl-datetimeutil"

    fun pickDateTime(context: Context, callback: (ZonedDateTime) -> Unit) {
        val currentDateTime = Calendar.getInstance()
        val startYear = currentDateTime.get(Calendar.YEAR)
        val startMonth = currentDateTime.get(Calendar.MONTH)
        val startDay = currentDateTime.get(Calendar.DAY_OF_MONTH)
        val startHour = currentDateTime.get(Calendar.HOUR_OF_DAY)
        val startMinute = currentDateTime.get(Calendar.MINUTE)

        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val dateTime = ZonedDateTime.of(
                            year,
                            month + 1,
                            day,
                            hour,
                            minute,
                            0,
                            0,
                            ZoneId.systemDefault()
                        )
                        Log.d(TAG, "User picked datatime $dateTime")
                        callback(dateTime)
                    },
                    startHour,
                    startMinute,
                    false
                ).show()
            },
            startYear,
            startMonth,
            startDay
        ).show()
    }
}