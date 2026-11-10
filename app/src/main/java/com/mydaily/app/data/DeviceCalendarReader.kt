package com.mydaily.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.mydaily.app.model.ConnectedCalendarAccount
import com.mydaily.app.model.ItemKind
import com.mydaily.app.model.ItemSource
import com.mydaily.app.model.UnifiedTimelineItem
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

object DeviceCalendarReader {

    fun hasCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getAvailableCalendars(
        context: Context,
        disabledCalendarIds: Set<String> = emptySet()
    ): List<ConnectedCalendarAccount> {
        if (!hasCalendarPermission(context)) return emptyList()

        val accounts = mutableListOf<ConnectedCalendarAccount>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.VISIBLE
        )

        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.ACCOUNT_NAME} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountNameIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                val accountTypeIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)

                while (cursor.moveToNext()) {
                    val calId = cursor.getLong(idIdx).toString()
                    val displayName = cursor.getString(nameIdx) ?: "Calendar"
                    val accountName = cursor.getString(accountNameIdx) ?: "Device Account"
                    val accountType = cursor.getString(accountTypeIdx) ?: ""

                    val source = classifyCalendarSource(accountType, accountName, displayName)
                    val isEnabled = !disabledCalendarIds.contains("dev_cal_$calId")

                    accounts.add(
                        ConnectedCalendarAccount(
                            id = "dev_cal_$calId",
                            name = displayName,
                            accountName = accountName,
                            source = source,
                            connectionType = when (source) {
                                ItemSource.GOOGLE_CALENDAR -> "Google Account (Device Sync)"
                                ItemSource.OUTLOOK_CALENDAR -> "Outlook / Exchange (Device Sync)"
                                else -> "System Calendar ($accountType)"
                            },
                            isEnabled = isEnabled
                        )
                    )
                }
            }
        }

        return accounts
    }

    fun queryEventsInRange(
        context: Context,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        disabledCalendarIds: Set<String> = emptySet()
    ): List<UnifiedTimelineItem> {
        if (!hasCalendarPermission(context)) return emptyList()

        val items = mutableListOf<UnifiedTimelineItem>()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, rangeStartMillis)
        ContentUris.appendId(builder, rangeEndMillis)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Instances.ORGANIZER
        )

        runCatching {
            // Do not filter by VISIBLE = 1 so that Outlook or secondary Google calendars
            // that are synced to the device are always discovered and controlled inside MyDaily.
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val eventIdIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val descIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
                val locIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                val beginIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                val calIdIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
                val calNameIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
                val accountNameIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                val accountTypeIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
                val organizerIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ORGANIZER)

                while (cursor.moveToNext()) {
                    val calId = cursor.getLong(calIdIdx).toString()
                    if (disabledCalendarIds.contains("dev_cal_$calId")) continue

                    val eventId = cursor.getLong(eventIdIdx)
                    val title = cursor.getString(titleIdx)?.takeIf { it.isNotBlank() } ?: "(No title)"
                    val description = cursor.getString(descIdx) ?: ""
                    val location = cursor.getString(locIdx) ?: ""
                    val rawStartMillis = cursor.getLong(beginIdx)
                    val rawEndMillis = cursor.getLong(endIdx)
                    val isAllDay = cursor.getInt(allDayIdx) == 1

                    // CalendarContract stores ALL_DAY events at UTC midnight.
                    // Convert them to local day start (00:00) and end (24:00) so they
                    // accurately collide with timed events on that local calendar date.
                    val (startMillis, endMillis) = if (isAllDay) {
                        val localZone = ZoneId.systemDefault()
                        val startUtcDate = Instant.ofEpochMilli(rawStartMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        val endUtcDate = Instant.ofEpochMilli(rawEndMillis).atZone(ZoneOffset.UTC).toLocalDate()
                            .let { if (it.isAfter(startUtcDate)) it else startUtcDate.plusDays(1) }
                        startUtcDate.atStartOfDay(localZone).toInstant().toEpochMilli() to
                            endUtcDate.atStartOfDay(localZone).toInstant().toEpochMilli()
                    } else {
                        rawStartMillis to if (rawEndMillis > rawStartMillis) rawEndMillis else rawStartMillis + 30 * 60_000L
                    }

                    val calName = cursor.getString(calNameIdx) ?: "Calendar"
                    val accountName = cursor.getString(accountNameIdx) ?: ""
                    val accountType = cursor.getString(accountTypeIdx) ?: ""
                    val organizer = cursor.getString(organizerIdx) ?: ""

                    val source = classifyCalendarSource(accountType, accountName, calName)
                    val kind = if (source == ItemSource.OUTLOOK_CALENDAR || organizer.isNotBlank()) {
                        ItemKind.APPOINTMENT
                    } else {
                        ItemKind.EVENT
                    }

                    items.add(
                        UnifiedTimelineItem(
                            id = "sys_${eventId}_$startMillis",
                            title = title,
                            subtitle = if (accountName.isNotBlank()) "$calName • $accountName" else calName,
                            description = description,
                            location = location,
                            startTimeMillis = startMillis,
                            endTimeMillis = endMillis,
                            isAllDay = isAllDay,
                            source = source,
                            kind = kind,
                            isEditable = true
                        )
                    )
                }
            }
        }

        return items
    }

    fun classifyCalendarSource(
        accountType: String,
        accountName: String,
        calendarName: String
    ): ItemSource {
        val combined = "$accountType $accountName $calendarName".lowercase()
        return when {
            combined.contains("outlook") ||
                combined.contains("microsoft") ||
                combined.contains("exchange") ||
                combined.contains("office365") ||
                combined.contains("hotmail") ||
                combined.contains("live.com") -> ItemSource.OUTLOOK_CALENDAR

            combined.contains("com.google") ||
                combined.contains("gmail.com") ||
                combined.contains("googlemail.com") ||
                combined.contains("google") -> ItemSource.GOOGLE_CALENDAR

            else -> ItemSource.DEVICE_CALENDAR
        }
    }
}
