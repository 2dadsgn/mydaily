package com.mydaily.app.domain

import com.mydaily.app.model.CollisionDetail
import com.mydaily.app.model.CollisionPair
import com.mydaily.app.model.CollisionSeverity
import com.mydaily.app.model.DayScheduleSummary
import com.mydaily.app.model.ItemSource
import com.mydaily.app.model.UnifiedTimelineItem
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/**
 * Detects time collisions across Google Calendar events, Outlook appointments,
 * and scheduled Google Tasks, annotating each item and computing collision pairs.
 */
object CollisionDetector {

    data class DetectionResult(
        val annotatedItems: List<UnifiedTimelineItem>,
        val collisionPairs: List<CollisionPair>,
        val totalOverlapMinutes: Int
    )

    /**
     * Analyzes a list of timeline items, detects all pairwise time overlaps among
     * active timed items (events, appointments, and non-completed timed tasks),
     * and returns annotated items sorted chronologically plus a list of collision pairs.
     */
    fun analyze(
        rawItems: List<UnifiedTimelineItem>,
        ignoreCompletedTasks: Boolean = true
    ): DetectionResult {
        val sorted = rawItems
            .map { it.copy(collisions = emptyList()) }
            .sortedWith(
                compareByDescending<UnifiedTimelineItem> { it.isAllDay }
                    .thenBy { it.startTimeMillis }
                    .thenBy { it.endTimeMillis }
                    .thenBy { it.title }
            )

        val collisionDetailsByItemId = mutableMapOf<String, MutableList<CollisionDetail>>()
        val pairs = mutableListOf<CollisionPair>()

        for (i in sorted.indices) {
            val first = sorted[i]
            if (!isEligibleForCollision(first, ignoreCompletedTasks)) continue

            for (j in i + 1 until sorted.size) {
                val second = sorted[j]
                if (!isEligibleForCollision(second, ignoreCompletedTasks)) continue

                // Since timed items are sorted by startTimeMillis, if second starts at or after
                // first ends, no subsequent timed item can overlap with first.
                if (second.startTimeMillis >= first.endTimeMillis) break

                val overlapStart = max(first.startTimeMillis, second.startTimeMillis)
                val overlapEnd = min(first.endTimeMillis, second.endTimeMillis)

                if (overlapStart < overlapEnd) {
                    val overlapMinutes = ((overlapEnd - overlapStart) / 60_000L).toInt().coerceAtLeast(1)
                    val isCrossProvider = first.source != second.source
                    val minItemDuration = min(first.durationMinutes, second.durationMinutes)
                    val severity = if (
                        overlapMinutes >= 30 ||
                        overlapMinutes >= minItemDuration ||
                        first.startTimeMillis == second.startTimeMillis
                    ) {
                        CollisionSeverity.HIGH_CONFLICT
                    } else {
                        CollisionSeverity.PARTIAL_OVERLAP
                    }

                    collisionDetailsByItemId.getOrPut(first.id) { mutableListOf() }.add(
                        CollisionDetail(
                            otherItemId = second.id,
                            otherTitle = second.title,
                            otherSource = second.source,
                            otherKind = second.kind,
                            overlapStartMillis = overlapStart,
                            overlapEndMillis = overlapEnd,
                            overlapMinutes = overlapMinutes,
                            severity = severity
                        )
                    )

                    collisionDetailsByItemId.getOrPut(second.id) { mutableListOf() }.add(
                        CollisionDetail(
                            otherItemId = first.id,
                            otherTitle = first.title,
                            otherSource = first.source,
                            otherKind = first.kind,
                            overlapStartMillis = overlapStart,
                            overlapEndMillis = overlapEnd,
                            overlapMinutes = overlapMinutes,
                            severity = severity
                        )
                    )

                    pairs.add(
                        CollisionPair(
                            id = "${first.id}__${second.id}",
                            first = first,
                            second = second,
                            overlapStartMillis = overlapStart,
                            overlapEndMillis = overlapEnd,
                            overlapMinutes = overlapMinutes,
                            isCrossProvider = isCrossProvider,
                            severity = severity
                        )
                    )
                }
            }
        }

        val annotated = sorted.map { item ->
            val details = collisionDetailsByItemId[item.id].orEmpty()
            item.copy(collisions = details)
        }

        val totalOverlapMinutes = pairs.sumOf { it.overlapMinutes }

        return DetectionResult(
            annotatedItems = annotated,
            collisionPairs = pairs,
            totalOverlapMinutes = totalOverlapMinutes
        )
    }

    /**
     * Builds a complete summary for a given date.
     */
    fun buildDaySummary(
        date: LocalDate,
        allItems: List<UnifiedTimelineItem>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): DayScheduleSummary {
        val dayItems = allItems.filter { it.occursOnDate(date, zoneId) }
        val result = analyze(dayItems)
        return DayScheduleSummary(
            date = date,
            items = result.annotatedItems,
            collisionPairs = result.collisionPairs,
            totalOverlapMinutes = result.totalOverlapMinutes,
            googleCalendarCount = result.annotatedItems.count { it.source == ItemSource.GOOGLE_CALENDAR },
            outlookCalendarCount = result.annotatedItems.count { it.source == ItemSource.OUTLOOK_CALENDAR },
            googleTasksCount = result.annotatedItems.count { it.source == ItemSource.GOOGLE_TASKS }
        )
    }

    /**
     * Finds the next available collision-free time slot on the same day for rescheduling
     * a colliding item of [durationMinutes] starting after [afterTimeMillis].
     */
    fun findNextFreeSlot(
        dayItems: List<UnifiedTimelineItem>,
        movingItemId: String,
        afterTimeMillis: Long,
        durationMinutes: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Pair<Long, Long> {
        val durationMillis = durationMinutes * 60_000L
        val others = dayItems
            .filter { it.id != movingItemId && !it.isAllDay && !it.isCompleted }
            .sortedBy { it.startTimeMillis }

        // Round candidate start up to the next 15-minute boundary
        val fifteenMin = 15 * 60_000L
        var candidateStart = ((afterTimeMillis + fifteenMin - 1) / fifteenMin) * fifteenMin

        for (other in others) {
            val candidateEnd = candidateStart + durationMillis
            if (candidateEnd <= other.startTimeMillis) {
                return candidateStart to candidateEnd
            }
            if (candidateStart < other.endTimeMillis) {
                candidateStart = ((other.endTimeMillis + fifteenMin - 1) / fifteenMin) * fifteenMin
            }
        }

        return candidateStart to (candidateStart + durationMillis)
    }

    private fun isEligibleForCollision(
        item: UnifiedTimelineItem,
        ignoreCompletedTasks: Boolean
    ): Boolean {
        if (item.isAllDay) return false
        if (ignoreCompletedTasks && item.isCompleted) return false
        return item.endTimeMillis > item.startTimeMillis
    }
}

