package com.mydaily.app.data

import android.content.Context
import com.mydaily.app.domain.CollisionDetector
import com.mydaily.app.model.CollisionPair
import com.mydaily.app.model.ConnectedCalendarAccount
import com.mydaily.app.model.DayScheduleSummary
import com.mydaily.app.model.IcsFeedConfig
import com.mydaily.app.model.ItemKind
import com.mydaily.app.model.ItemSource
import com.mydaily.app.model.UnifiedTimelineItem
import com.mydaily.app.model.UpcomingCollisionsOverview
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ScheduleRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isWidgetCollisionsOnly(): Boolean {
        return prefs.getBoolean(KEY_WIDGET_COLLISIONS_ONLY, false)
    }

    fun toggleWidgetCollisionsOnly(): Boolean {
        val next = !isWidgetCollisionsOnly()
        prefs.edit().putBoolean(KEY_WIDGET_COLLISIONS_ONLY, next).apply()
        return next
    }

    fun getGoogleTasksToken(): String {
        return prefs.getString(KEY_GOOGLE_TASKS_TOKEN, "") ?: ""
    }

    fun setGoogleTasksToken(token: String) {
        prefs.edit().putString(KEY_GOOGLE_TASKS_TOKEN, token.trim()).apply()
    }

    fun getGoogleAccountEmail(): String {
        return prefs.getString(KEY_GOOGLE_ACCOUNT_EMAIL, "") ?: ""
    }

    fun setGoogleAccountEmail(email: String) {
        prefs.edit().putString(KEY_GOOGLE_ACCOUNT_EMAIL, email.trim()).apply()
    }

    fun getDisabledCalendarIds(): Set<String> {
        return prefs.getStringSet(KEY_DISABLED_CALENDARS, emptySet()) ?: emptySet()
    }

    fun toggleCalendarAccount(accountId: String) {
        val current = getDisabledCalendarIds().toMutableSet()
        if (current.contains(accountId)) {
            current.remove(accountId)
        } else {
            current.add(accountId)
        }
        prefs.edit().putStringSet(KEY_DISABLED_CALENDARS, current).apply()
    }

    fun getCompletedItemIds(): Set<String> {
        return prefs.getStringSet(KEY_COMPLETED_IDS, emptySet()) ?: emptySet()
    }

    fun toggleTaskCompleted(itemId: String) {
        val current = getCompletedItemIds().toMutableSet()
        if (current.contains(itemId)) {
            current.remove(itemId)
        } else {
            current.add(itemId)
        }
        prefs.edit().putStringSet(KEY_COMPLETED_IDS, current).apply()
    }

    fun saveTimeOverride(itemId: String, newStartMillis: Long, newEndMillis: Long) {
        val overridesObj = getTimeOverridesJson()
        val entry = JSONObject().apply {
            put("start", newStartMillis)
            put("end", newEndMillis)
        }
        overridesObj.put(itemId, entry)
        prefs.edit().putString(KEY_TIME_OVERRIDES, overridesObj.toString()).apply()
    }

    fun clearTimeOverrides() {
        prefs.edit().remove(KEY_TIME_OVERRIDES).apply()
    }

    private fun getTimeOverridesJson(): JSONObject {
        val raw = prefs.getString(KEY_TIME_OVERRIDES, "{}") ?: "{}"
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }

    fun addCustomItem(item: UnifiedTimelineItem) {
        val current = getCustomItems().toMutableList()
        current.removeAll { it.id == item.id }
        current.add(item)
        saveItemsToJsonKey(KEY_CUSTOM_ITEMS, current)
    }

    fun deleteCustomItem(itemId: String) {
        val current = getCustomItems().filterNot { it.id == itemId }
        saveItemsToJsonKey(KEY_CUSTOM_ITEMS, current)
    }

    fun getCustomItems(): List<UnifiedTimelineItem> {
        return readItemsFromJsonKey(KEY_CUSTOM_ITEMS)
    }

    fun getIcsFeeds(): List<IcsFeedConfig> {
        val raw = prefs.getString(KEY_ICS_FEEDS, "[]") ?: "[]"
        val list = mutableListOf<IcsFeedConfig>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    IcsFeedConfig(
                        id = obj.optString("id"),
                        label = obj.optString("label"),
                        url = obj.optString("url"),
                        source = runCatching {
                            ItemSource.valueOf(obj.optString("source"))
                        }.getOrDefault(ItemSource.OUTLOOK_CALENDAR),
                        lastSyncedMillis = obj.optLong("lastSyncedMillis", 0L),
                        eventCount = obj.optInt("eventCount", 0)
                    )
                )
            }
        }
        return list
    }

    fun addIcsFeed(label: String, url: String, source: ItemSource): IcsFeedConfig {
        val feed = IcsFeedConfig(
            id = "ics_feed_${UUID.randomUUID()}",
            label = label.trim().ifEmpty { "${source.displayName} Feed" },
            url = url.trim(),
            source = source
        )
        val updated = getIcsFeeds().toMutableList().apply { add(feed) }
        saveIcsFeeds(updated)
        return feed
    }

    fun removeIcsFeed(feedId: String) {
        val updated = getIcsFeeds().filterNot { it.id == feedId }
        saveIcsFeeds(updated)
        val cached = readItemsFromJsonKey(KEY_CACHED_REMOTE_ITEMS)
            .filterNot { it.subtitle.contains(feedId) }
        saveItemsToJsonKey(KEY_CACHED_REMOTE_ITEMS, cached)
    }

    private fun saveIcsFeeds(feeds: List<IcsFeedConfig>) {
        val arr = JSONArray()
        for (feed in feeds) {
            arr.put(
                JSONObject().apply {
                    put("id", feed.id)
                    put("label", feed.label)
                    put("url", feed.url)
                    put("source", feed.source.name)
                    put("lastSyncedMillis", feed.lastSyncedMillis)
                    put("eventCount", feed.eventCount)
                }
            )
        }
        prefs.edit().putString(KEY_ICS_FEEDS, arr.toString()).apply()
    }

    /**
     * Performs network sync of configured ICS Calendar feeds (Google/Outlook)
     * and Google Tasks REST API if configured. Must be called from a background dispatcher.
     */
    fun syncRemoteSources(): String {
        val remoteItems = mutableListOf<UnifiedTimelineItem>()
        val statusParts = mutableListOf<String>()

        // 1. Sync ICS Feeds (Outlook & Google Calendar URLs)
        val updatedFeeds = mutableListOf<IcsFeedConfig>()
        for (feed in getIcsFeeds()) {
            try {
                val parsed = IcsParser.fetchAndParseIcsUrl(
                    urlString = feed.url,
                    source = feed.source,
                    feedLabel = feed.label
                )
                remoteItems.addAll(parsed)
                updatedFeeds.add(
                    feed.copy(
                        lastSyncedMillis = System.currentTimeMillis(),
                        eventCount = parsed.size
                    )
                )
                statusParts.add("${feed.label}: ${parsed.size} events")
            } catch (e: Exception) {
                updatedFeeds.add(feed)
                statusParts.add("${feed.label}: sync error (${e.message ?: "network"})")
            }
        }
        saveIcsFeeds(updatedFeeds)

        // 2. Sync Google Tasks & Google Calendar REST APIs if token is set
        val token = getGoogleTasksToken()
        if (token.isNotBlank()) {
            try {
                val apiTasks = GoogleTasksClient.fetchGoogleTasks(token)
                remoteItems.addAll(apiTasks)
                statusParts.add("Google Tasks: ${apiTasks.size} tasks")
            } catch (e: Exception) {
                statusParts.add("Google Tasks: error (${e.message ?: "auth/network"})")
            }

            try {
                val apiCalEvents = GoogleCalendarApiClient.fetchGoogleCalendarEvents(token)
                remoteItems.addAll(apiCalEvents)
                statusParts.add("Google Cal API: ${apiCalEvents.size} events")
            } catch (_: Exception) {
                // Token might only have tasks.readonly scope; DeviceCalendarReader handles device calendar sync
            }
        }

        saveItemsToJsonKey(KEY_CACHED_REMOTE_ITEMS, remoteItems)

        val deviceCalCount = DeviceCalendarReader.getAvailableCalendars(
            context,
            getDisabledCalendarIds()
        ).size
        statusParts.add(0, "Device calendars: $deviceCalCount")

        return statusParts.joinToString(" • ")
    }

    /**
     * Loads and merges all REAL timeline items across:
     * 1. Android System Calendar Provider (Google Calendar, Outlook, Exchange accounts on phone)
     * 2. Synced Remote ICS Feeds & Google Tasks REST API items
     * 3. User-created custom Google/Outlook events & Google Tasks
     * Applies any completion states and time overrides, then runs collision detection.
     */
    fun getAllTimelineItems(zoneId: ZoneId = ZoneId.systemDefault()): List<UnifiedTimelineItem> {
        val today = LocalDate.now(zoneId)
        val rangeStart = today.minusDays(60).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val rangeEnd = today.plusDays(90).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val disabledIds = getDisabledCalendarIds()
        val combined = mutableListOf<UnifiedTimelineItem>()

        // 1. Device Calendar Provider (Google & Outlook accounts on phone)
        if (DeviceCalendarReader.hasCalendarPermission(context)) {
            combined.addAll(
                DeviceCalendarReader.queryEventsInRange(
                    context = context,
                    rangeStartMillis = rangeStart,
                    rangeEndMillis = rangeEnd,
                    disabledCalendarIds = disabledIds
                )
            )
        }

        // 2. Cached remote ICS + Google Tasks API items
        combined.addAll(readItemsFromJsonKey(KEY_CACHED_REMOTE_ITEMS))

        // 3. In-app created events, appointments, and Google Tasks
        combined.addAll(getCustomItems())

        // Deduplicate items that share the same source, title, and startTimeMillis
        val deduplicated = combined.distinctBy {
            "${it.source.name}|${it.title.trim().lowercase()}|${it.startTimeMillis}"
        }

        // Apply completion states (always keep exact true times from Google Calendar & Outlook)
        val completedIds = getCompletedItemIds()

        val normalized = deduplicated.map { item ->
            val isCompleted = item.isCompleted || completedIds.contains(item.id)
            item.copy(
                isCompleted = isCompleted
            )
        }

        return CollisionDetector.analyze(normalized).annotatedItems
    }

    fun getDaySummary(
        date: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): DayScheduleSummary {
        val allItems = getAllTimelineItems(zoneId)
        return CollisionDetector.buildDaySummary(date, allItems, zoneId)
    }

    fun getWeekSummaries(
        startDate: LocalDate = LocalDate.now(),
        days: Int = 7,
        activeOnly: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<DayScheduleSummary> {
        val rawItems = getAllTimelineItems(zoneId)
        val items = if (activeOnly) {
            rawItems.filter { it.endTimeMillis > nowMillis && !it.isCompleted }
        } else {
            rawItems
        }
        return (0 until days).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            CollisionDetector.buildDaySummary(date, items, zoneId)
        }
    }

    /**
     * Returns the earliest upcoming timestamp when a currently active/upcoming event ends
     * (or next local midnight, whichever is sooner), so the widget can auto-refresh
     * the moment an active event becomes a past event.
     */
    fun getNextActiveExpirationMillis(
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val nextMidnight = LocalDate.now(zoneId).plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val earliestEnd = getAllTimelineItems(zoneId)
            .asSequence()
            .filter { !it.isCompleted && it.endTimeMillis > nowMillis }
            .map { it.endTimeMillis }
            .minOrNull()

        return if (earliestEnd != null && earliestEnd < nextMidnight) {
            earliestEnd
        } else {
            nextMidnight
        }
    }

    /**
     * Evaluates Today's schedule AND scans the next 30 days for collisions on other days.
     */
    fun getUpcomingCollisionsOverview(
        today: LocalDate = LocalDate.now(),
        daysAhead: Int = 30,
        activeOnly: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): UpcomingCollisionsOverview {
        val rawItems = getAllTimelineItems(zoneId)
        val allItems = if (activeOnly) {
            rawItems.filter { it.endTimeMillis > nowMillis && !it.isCompleted }
        } else {
            rawItems
        }
        val todaySummary = CollisionDetector.buildDaySummary(today, allItems, zoneId)

        val otherDaysWithCollisions = mutableListOf<DayScheduleSummary>()
        val allCollisionsByDate = mutableListOf<Pair<LocalDate, CollisionPair>>()

        todaySummary.collisionPairs.forEach { pair ->
            allCollisionsByDate.add(today to pair)
        }

        for (offset in 1..daysAhead) {
            val date = today.plusDays(offset.toLong())
            val summary = CollisionDetector.buildDaySummary(date, allItems, zoneId)
            if (summary.collisionPairs.isNotEmpty()) {
                otherDaysWithCollisions.add(summary)
                summary.collisionPairs.forEach { pair ->
                    allCollisionsByDate.add(date to pair)
                }
            }
        }

        return UpcomingCollisionsOverview(
            todaySummary = todaySummary,
            otherDaysWithCollisions = otherDaysWithCollisions,
            totalOtherDayCollisionsCount = otherDaysWithCollisions.sumOf { it.collisionPairs.size },
            allCollisionsByDate = allCollisionsByDate
        )
    }

    fun getNotifiedCollisionKeys(): Set<String> {
        return prefs.getStringSet(KEY_NOTIFIED_COLLISIONS, emptySet()) ?: emptySet()
    }

    fun saveNotifiedCollisionKeys(keys: Set<String>) {
        prefs.edit().putStringSet(KEY_NOTIFIED_COLLISIONS, keys).apply()
    }

    fun hasAnyConnectedSource(): Boolean {
        val hasDeviceCals = DeviceCalendarReader.hasCalendarPermission(context) &&
            DeviceCalendarReader.getAvailableCalendars(context, getDisabledCalendarIds()).isNotEmpty()
        val hasIcs = getIcsFeeds().isNotEmpty()
        val hasTasksAuth = getGoogleTasksToken().isNotBlank() || getGoogleAccountEmail().isNotBlank()
        val hasCustom = getCustomItems().isNotEmpty()
        return hasDeviceCals || hasIcs || hasTasksAuth || hasCustom
    }

    fun getConnectedAccountsSummary(): List<ConnectedCalendarAccount> {
        val result = mutableListOf<ConnectedCalendarAccount>()
        val disabled = getDisabledCalendarIds()
        val allItems = getAllTimelineItems()

        // 1. Discovered Device Calendars (Google Calendar, Outlook, Exchange, System)
        val deviceCalendars = DeviceCalendarReader.getAvailableCalendars(context, disabled).map { acct ->
            val count = allItems.count { it.subtitle.contains(acct.name) }
            acct.copy(itemCount = count)
        }
        result.addAll(deviceCalendars)

        // 2. Connected ICS feeds
        for (feed in getIcsFeeds()) {
            result.add(
                ConnectedCalendarAccount(
                    id = feed.id,
                    name = feed.label,
                    accountName = feed.url,
                    source = feed.source,
                    connectionType = "ICS Calendar URL",
                    isEnabled = true,
                    itemCount = feed.eventCount
                )
            )
        }

        // 3. Google Tasks account status if connected
        val googleEmail = getGoogleAccountEmail()
        val hasToken = getGoogleTasksToken().isNotBlank()
        val tasksCount = allItems.count { it.source == ItemSource.GOOGLE_TASKS }
        if (googleEmail.isNotBlank() || hasToken || tasksCount > 0) {
            result.add(
                ConnectedCalendarAccount(
                    id = "connected_gtasks",
                    name = "Google Tasks",
                    accountName = when {
                        googleEmail.isNotBlank() && hasToken -> "$googleEmail (Cloud Sync Active)"
                        googleEmail.isNotBlank() -> "$googleEmail (Selected)"
                        hasToken -> "OAuth Token Connected"
                        else -> "In-App Scheduled Tasks"
                    },
                    source = ItemSource.GOOGLE_TASKS,
                    connectionType = "Google Tasks",
                    isEnabled = true,
                    itemCount = tasksCount
                )
            )
        }

        return result
    }

    private fun saveItemsToJsonKey(key: String, items: List<UnifiedTimelineItem>) {
        val arr = JSONArray()
        for (item in items) {
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("subtitle", item.subtitle)
                    put("description", item.description)
                    put("location", item.location)
                    put("startTimeMillis", item.startTimeMillis)
                    put("endTimeMillis", item.endTimeMillis)
                    put("isAllDay", item.isAllDay)
                    put("source", item.source.name)
                    put("kind", item.kind.name)
                    put("isCompleted", item.isCompleted)
                }
            )
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun readItemsFromJsonKey(key: String): List<UnifiedTimelineItem> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        val list = mutableListOf<UnifiedTimelineItem>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    UnifiedTimelineItem(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        subtitle = obj.optString("subtitle"),
                        description = obj.optString("description"),
                        location = obj.optString("location"),
                        startTimeMillis = obj.optLong("startTimeMillis"),
                        endTimeMillis = obj.optLong("endTimeMillis"),
                        isAllDay = obj.optBoolean("isAllDay", false),
                        source = runCatching {
                            ItemSource.valueOf(obj.optString("source"))
                        }.getOrDefault(ItemSource.GOOGLE_CALENDAR),
                        kind = runCatching {
                            ItemKind.valueOf(obj.optString("kind"))
                        }.getOrDefault(ItemKind.EVENT),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        isEditable = true
                    )
                )
            }
        }
        return list
    }

    companion object {
        private const val PREFS_NAME = "mydaily_schedule_prefs"
        private const val KEY_WIDGET_COLLISIONS_ONLY = "widget_collisions_only"
        private const val KEY_GOOGLE_TASKS_TOKEN = "google_tasks_token"
        private const val KEY_GOOGLE_ACCOUNT_EMAIL = "google_account_email"
        private const val KEY_DISABLED_CALENDARS = "disabled_calendars"
        private const val KEY_COMPLETED_IDS = "completed_item_ids"
        private const val KEY_TIME_OVERRIDES = "time_overrides_json"
        private const val KEY_CUSTOM_ITEMS = "custom_items_json"
        private const val KEY_CACHED_REMOTE_ITEMS = "cached_remote_items_json"
        private const val KEY_ICS_FEEDS = "ics_feeds_json"
        private const val KEY_NOTIFIED_COLLISIONS = "notified_collision_keys"
    }
}
