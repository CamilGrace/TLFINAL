package com.example.tlfinal // Or your package

import android.text.format.DateUtils
import java.util.Date
import java.util.concurrent.TimeUnit

object TimeFormatter {

    // For Inbox: "5 min", "1 hour", "Yesterday", "Mar 10"
    fun getShortRelativeTime(date: Date): String {
        val now = System.currentTimeMillis()
        val time = date.time
        val diff = now - time

        return when {
            diff < DateUtils.MINUTE_IN_MILLIS -> "Just now"
            diff < DateUtils.HOUR_IN_MILLIS -> "${diff / DateUtils.MINUTE_IN_MILLIS} min"
            diff < DateUtils.DAY_IN_MILLIS -> "${diff / DateUtils.HOUR_IN_MILLIS} hour"
            DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> "Yesterday" // Check if it was yesterday
            else -> android.text.format.DateFormat.format("MMM d", date).toString() // e.g., "Mar 10"
        }
    }

    // For Audio Duration: "03:15"
    fun formatDuration(milliseconds: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}