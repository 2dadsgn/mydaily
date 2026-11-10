package com.mydaily.app.data

import com.mydaily.app.model.ItemKind
import com.mydaily.app.model.ItemSource
import com.mydaily.app.model.UnifiedTimelineItem
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Connects to the Google Tasks REST API v1 (`https://tasks.googleapis.com/tasks/v1`)
 * when an OAuth 2.0 access token is provided.
 */
object GoogleTasksClient {

    private const val BASE_URL = "https://tasks.googleapis.com/tasks/v1"

    fun fetchGoogleTasks(
        oauthAccessToken: String,
        defaultTaskDurationMinutes: Int = 30,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<UnifiedTimelineItem> {
        val token = oauthAccessToken.trim()
        if (token.isEmpty()) return emptyList()

        val result = mutableListOf<UnifiedTimelineItem>()
        val listsJson = executeGet("$BASE_URL/users/@me/lists?maxResults=20", token)
        val listsArray = JSONObject(listsJson).optJSONArray("items") ?: return emptyList()

        for (i in 0 until listsArray.length()) {
            val listObj = listsArray.getJSONObject(i)
            val listId = listObj.optString("id")
            val listTitle = listObj.optString("title", "My Tasks")
            if (listId.isBlank()) continue

            val tasksJson = executeGet(
                "$BASE_URL/lists/$listId/tasks?showCompleted=true&showHidden=true&maxResults=100",
                token
            )
            val tasksArray = JSONObject(tasksJson).optJSONArray("items") ?: continue

            for (j in 0 until tasksArray.length()) {
                val taskObj = tasksArray.getJSONObject(j)
                val taskId = taskObj.optString("id")
                val title = taskObj.optString("title").trim()
                if (title.isEmpty()) continue

                val notes = taskObj.optString("notes", "")
                val status = taskObj.optString("status", "needsAction")
                val isCompleted = status.equals("completed", ignoreCase = true)
                val dueStr = taskObj.optString("due", "")

                val startMillis = parseDueTimestamp(dueStr, zoneId)
                val endMillis = startMillis + defaultTaskDurationMinutes * 60_000L

                result.add(
                    UnifiedTimelineItem(
                        id = "gtask_api_$taskId",
                        title = title,
                        subtitle = "Google Tasks • $listTitle",
                        description = notes,
                        location = "",
                        startTimeMillis = startMillis,
                        endTimeMillis = endMillis,
                        isAllDay = false,
                        source = ItemSource.GOOGLE_TASKS,
                        kind = ItemKind.GOOGLE_TASK,
                        isCompleted = isCompleted,
                        isEditable = true
                    )
                )
            }
        }

        return result
    }

    private fun parseDueTimestamp(dueRfc3339: String, zoneId: ZoneId): Long {
        if (dueRfc3339.isBlank()) {
            // Default unscheduled tasks to 10:00 AM today
            return LocalDate.now(zoneId).atTime(10, 0).atZone(zoneId).toInstant().toEpochMilli()
        }
        return try {
            val instant = Instant.parse(dueRfc3339)
            val zoned = instant.atZone(zoneId)
            // Google Tasks API often returns midnight UTC for date-only due dates;
            // place date-only tasks at 10:00 AM local time so they appear on the daily timeline.
            if (dueRfc3339.endsWith("T00:00:00.000Z") || dueRfc3339.endsWith("T00:00:00Z")) {
                val localDate = Instant.parse(dueRfc3339).atZone(ZoneId.of("UTC")).toLocalDate()
                localDate.atTime(10, 0).atZone(zoneId).toInstant().toEpochMilli()
            } else {
                zoned.toInstant().toEpochMilli()
            }
        } catch (_: Exception) {
            LocalDate.now(zoneId).atTime(10, 0).atZone(zoneId).toInstant().toEpochMilli()
        }
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

