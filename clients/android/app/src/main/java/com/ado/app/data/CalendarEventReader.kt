package com.ado.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CalendarDailyItem(
    val name: String,
    val description: String = CALENDAR_ITEM_TAG,
)

data class CalendarReadResult(
    val items: List<CalendarDailyItem>,
    val errorMessage: String? = null,
)

const val CALENDAR_ITEM_TAG = "calendar"

class CalendarEventReader(private val contentResolver: ContentResolver) {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun readEventsForDate(date: LocalDate): CalendarReadResult = withContext(Dispatchers.IO) {
        try {
            val zone = ZoneId.systemDefault()
            val startMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(uriBuilder, startMillis)
            ContentUris.appendId(uriBuilder, endMillis)

            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
            )
            val items = mutableListOf<CalendarDailyItem>()
            val seen = mutableSetOf<String>()

            contentResolver.query(
                uriBuilder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                val eventIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)

                while (cursor.moveToNext()) {
                    val beginMillis = cursor.getLong(beginIndex)
                    val end = cursor.getLong(endIndex).takeIf { it > 0L } ?: beginMillis
                    if (beginMillis >= endMillis || end <= startMillis) continue

                    val title = cursor.getString(titleIndex)?.trim().orEmpty().ifBlank { "Calendar event" }
                    val allDay = cursor.getInt(allDayIndex) == 1
                    val itemName = if (allDay) {
                        title
                    } else {
                        val displayMillis = maxOf(beginMillis, startMillis)
                        val startTime = Instant.ofEpochMilli(displayMillis).atZone(zone).toLocalTime()
                        "${timeFormatter.format(startTime)} - $title"
                    }
                    val key = "${cursor.getLong(eventIdIndex)}|$beginMillis|${itemName.lowercase()}"
                    if (seen.add(key)) {
                        items += CalendarDailyItem(name = itemName)
                    }
                }
            } ?: return@withContext CalendarReadResult(emptyList())

            CalendarReadResult(items)
        } catch (_: SecurityException) {
            CalendarReadResult(emptyList(), "Calendar permission is not available.")
        } catch (_: Exception) {
            CalendarReadResult(emptyList(), "Calendar items could not be imported.")
        }
    }
}
