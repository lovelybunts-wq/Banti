package com.example.data.local

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

object StudyDateUtils {
    private fun fmt(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun todayKey(): String = fmt().format(Date())

    fun yesterdayKey(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return fmt().format(cal.time)
    }

    fun tomorrowKey(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return fmt().format(cal.time)
    }

    fun newValidId(): String = UUID.randomUUID().toString().replace("-", "").take(16)
}
