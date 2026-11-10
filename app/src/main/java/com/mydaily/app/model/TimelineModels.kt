package com.mydaily.app.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ItemSource(
    val displayName: String,
    val shortBadge: String,
    val colorHex: Long
) {
    GOOGLE_CALENDAR("Google Calendar", "G-CAL", 0xFF4285F4),
    OUTLOOK_CALENDAR("Outlook Calendar", "OUTLOOK", 0xFF0078D4),
    GOOGLE_TASKS("Google Tasks", "G-TASK", 0xFF0F9D58),
    DEVICE_CALENDAR("System Calendar", "CAL", 0xFF8E24AA)
}

enum class ItemKind(val label: String) {
    EVENT("Event"),
    APPOINTMENT("Appointment"),
    GOOGLE_TASK("Google Task")
}

enum class CollisionSeverity(val label: String) {
    HIGH_CONFLICT("Direct Collision"),
    PARTIAL_OVERLAP("Partial Overlap")
}

data class CollisionDetail(
    val otherItemId: String,
    val otherTitle: String,
    val otherSource: ItemSource,
    val otherKind: ItemKind,
    val overlapStartMillis: Long,
    val overlapEndMillis: Long,
    val overlapMinutes: Int,
    val severity: CollisionSeverity
)

data class CollisionPair(
    val id: String,
    val first: UnifiedTimelineItem,
    val second: UnifiedTimelineItem,
    val overlapStartMillis: Long,
    val overlapEndMillis: Long,
    val overlapMinutes: Int,
    val isCrossProvider: Boolean,
    val severity: CollisionSeverity
) {
    fun formattedOverlapWindow(zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (first.isAllDay && second.isAllDay) return "All Day"
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val start = Instant.ofEpochMilli(overlapStartMillis).atZone(zoneId).format(formatter)
        val end = Instant.ofEpochMilli(overlapEndMillis).atZone(zoneId).format(formatter)
        return "$start – $end"
    }
}

data class UnifiedTimelineItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val location: String = "",
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val isAllDay: Boolean = false,
    val source: ItemSource,
    val kind: ItemKind,
    val isCompleted: Boolean = false,
    val isEditable: Boolean = true,
    val collisions: List<CollisionDetail> = emptyList()
) {
    val hasCollision: Boolean
        get() = collisions.isNotEmpty()

    val durationMinutes: Int
        get() = ((endTimeMillis - startTimeMillis) / 60_000L).toInt().coerceAtLeast(1)

    fun formattedTimeRange(zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (isAllDay) return "All Day"
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val start = Instant.ofEpochMilli(startTimeMillis).atZone(zoneId).format(formatter)
        val end = Instant.ofEpochMilli(endTimeMillis).atZone(zoneId).format(formatter)
        return "$start – $end"
    }

    fun occursOnDate(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
        val dayStart = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return startTimeMillis < dayEnd && endTimeMillis > dayStart
    }
}

data class ConnectedCalendarAccount(
    val id: String,
    val name: String,
    val accountName: String,
    val source: ItemSource,
    val connectionType: String,
    val isEnabled: Boolean = true,
    val itemCount: Int = 0
)

data class IcsFeedConfig(
    val id: String,
    val label: String,
    val url: String,
    val source: ItemSource,
    val lastSyncedMillis: Long = 0L,
    val eventCount: Int = 0
)

data class DayScheduleSummary(
    val date: LocalDate,
    val items: List<UnifiedTimelineItem>,
    val collisionPairs: List<CollisionPair>,
    val totalOverlapMinutes: Int,
    val googleCalendarCount: Int,
    val outlookCalendarCount: Int,
    val googleTasksCount: Int
)

data class UpcomingCollisionsOverview(
    val todaySummary: DayScheduleSummary,
    val otherDaysWithCollisions: List<DayScheduleSummary>,
    val totalOtherDayCollisionsCount: Int,
    val allCollisionsByDate: List<Pair<LocalDate, CollisionPair>>
)


