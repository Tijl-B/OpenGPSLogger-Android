package eu.tijlb.opengpslogger.fragment

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.DatePicker
import androidx.fragment.app.DialogFragment
import java.time.LocalDate
import java.util.Calendar

class DatePickerFragment : DialogFragment(), DatePickerDialog.OnDateSetListener {

    interface OnDateSelectedListener {
        fun onDateSelected(identifier: String, localDate: LocalDate)
    }

    var listener: OnDateSelectedListener? = null
    var identifier: String = ""

    var initialYear: Int = 0
    var initialMonth: Int = 0
    var initialDay: Int = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val calendar = Calendar.getInstance()
        if (initialYear != 0) {
            calendar.set(initialYear, initialMonth, initialDay)
        }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        return DatePickerDialog(requireContext(), this, year, month, day)
    }

    override fun onDateSet(view: DatePicker?, year: Int, month: Int, dayOfMonth: Int) {
        val localDate = LocalDate.of(year, month + 1, dayOfMonth) // Calendar uses 0-based months
        listener?.onDateSelected(identifier, localDate)
    }

    fun setInitialDate(year: Int, month: Int, day: Int) {
        initialYear = year
        initialMonth = month - 1 // Calendar uses 0-based months
        initialDay = day
    }
}