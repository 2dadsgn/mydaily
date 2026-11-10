package com.mydaily.app.data

import com.mydaily.app.model.ItemKind
import com.mydaily.app.model.ItemSource
import com.mydaily.app.model.UnifiedTimelineItem
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Fetches Google Calendar events directly from the Google Calendar REST API v3
 * (`https://www.googleapis.com/calendar/v3`) when a Google OAuth 2.0 Bearer token is available.
 */
object GoogleCalendarApiClient {

    private const val BASE_URL = "https://www.googleapis.com/calendar/v3"

    fun fetchGoogleCalendarEvents(
        oauthAccessToken: String,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<UnifiedTimelineItem> {
        val token = oauthAccessToken.trim()
        if (token.isEmpty()) return emptyList()

        val today = LocalDate.now(zoneId)
        val timeMin = today.minusDays(7).atStartOfDay(zoneId).toInstant().toString()
        val timeMax = today.plusDays(30).atStartOfDay(zoneId).toInstant().toString()

        val encodedMin = URLEncoder.encode(timeMin, "UTF-8")
        val encodedMax = URLEncoder.encode(timeMax, "UTF-8")

        val url = "$BASE_URL/calendars/primary/events?singleEvents=true&orderBy=startTime" +
            "&timeMin=$encodedMin&timeMax=$encodedMax&maxResults=150"

        val jsonStr = executeGet(url, token)
        val root = JSONObject(jsonStr)
        val calendarSummary = root.optString("summary", "Google Calendar")
        val itemsArr = root.optJSONArray("items") ?: return emptyList()

        val events = mutableListOf<UnifiedTimelineItem>()
        for (i in 0 until itemsArr.length()) {
            val ev = itemsArr.getJSONObject(i)
            val status = ev.optString("status", "confirmed")
            if (status.equals("cancelled", ignoreCase = true)) continue

            val eventId = ev.optString("id")
            val title = ev.optString("summary", "(No title)")
            val description = ev.optString("description", "")
            val location = ev.optString("location", "")

            val startObj = ev.optJSONObject("start") ?: continue
            val endObj = ev.optJSONObject("end")

            val (startMillis, isAllDay) = parseEventTime(startObj, zoneId)
            val endMillis = if (endObj != null) {
                parseEventTime(endObj, zoneId).first
            } else {
                startMillis + 3_600_000L
            }

            if (startMillis > 0L) {
                events.add(
                    UnifiedTimelineItem(
                        id = "gcal_api_${eventId}_$startMillis",
                        title = title,
                        subtitle = "Google Calendar • $calendarSummary",
                        description = description,
                        location = location,
                        startTimeMillis = startMillis,
                        endTimeMillis = endMillis.coerceAtLeast(startMillis + 15 * 60_000L),
                        isAllDay = isAllDay,
                        source = ItemSource.GOOGLE_CALENDAR,
                        kind = ItemKind.EVENT,
                        isEditable = true
                    )
                )
            }
        }
        return events
    }

    private fun parseEventTime(timeObj: JSONObject, zoneId: ZoneId): Pair<Long, Boolean> {
        val dateTimeStr = timeObj.optString("dateTime", "")
        if (dateTimeStr.isNotBlank()) {
            return runCatching {
                Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(dateTimeStr)).toEpochMilli() to false
            }.getOrElse { 0L to false }
        }
        val dateStr = timeObj.optString("date", "")
        if (dateStr.isNotBlank()) {
            return runCatching {
                LocalDate.parse(dateStr).atStartOfDay(zoneId).toInstant().toEpochMilli() to true
            }.getOrElse { 0L to true }
        }
        return 0L to false
    }

    private fun executeGet(urlString: String, bearerToken: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Authorization", "Bearer $bearerToken")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

