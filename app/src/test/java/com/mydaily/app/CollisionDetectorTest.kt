package com.mydaily.app

import com.mydaily.app.data.DeviceCalendarReader
import com.mydaily.app.data.IcsParser
import com.mydaily.app.domain.CollisionDetector
import com.mydaily.app.model.ItemKind
import com.mydaily.app.model.ItemSource
import com.mydaily.app.model.UnifiedTimelineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CollisionDetectorTest {

    private val testZone = ZoneId.of("UTC")
    private val testDate = LocalDate.of(2026, 11, 8)

    private fun buildTestItems(): List<UnifiedTimelineItem> {
        fun millisAt(hour: Int, minute: Int): Long {
            return testDate.atTime(hour, minute).atZone(testZone).toInstant().toEpochMilli()
        }
        return listOf(
            UnifiedTimelineItem(
                id = "gcal_standup",
                title = "Engineering Sprint Standup",
                startTimeMillis = millisAt(9, 0),
                endTimeMillis = millisAt(9, 45),
                source = ItemSource.GOOGLE_CALENDAR,
                kind = ItemKind.EVENT
            ),
            UnifiedTimelineItem(
                id = "outlook_client_arch",
                title = "Q4 Client Architecture Review",
                startTimeMillis = millisAt(9, 15),
                endTimeMillis = millisAt(10, 15),
                source = ItemSource.OUTLOOK_CALENDAR,
                kind = ItemKind.APPOINTMENT
            ),
            UnifiedTimelineItem(
                id = "gcal_roadmap",
                title = "Product Design & UX Review",
                startTimeMillis = millisAt(11, 0),
                endTimeMillis = millisAt(12, 0),
                source = ItemSource.GOOGLE_CALENDAR,
                kind = ItemKind.EVENT
            ),
            UnifiedTimelineItem(
                id = "gtask_contract",
                title = "Send Signed Vendor Redlines",
                startTimeMillis = millisAt(11, 15),
                endTimeMillis = millisAt(11, 45),
                source = ItemSource.GOOGLE_TASKS,
                kind = ItemKind.GOOGLE_TASK
            ),
            UnifiedTimelineItem(
                id = "outlook_exec_1on1",
                title = "Executive 1:1 with VP of Product",
                startTimeMillis = millisAt(14, 0),
                endTimeMillis = millisAt(15, 0),
                source = ItemSource.OUTLOOK_CALENDAR,
                kind = ItemKind.APPOINTMENT
            ),
            UnifiedTimelineItem(
                id = "gtask_budget",
                title = "Prepare Q1 Hiring Budget Sheet",
                startTimeMillis = millisAt(14, 0),
                endTimeMillis = millisAt(14, 45),
                source = ItemSource.GOOGLE_TASKS,
                kind = ItemKind.GOOGLE_TASK
            )
        )
    }

    @Test
    fun `detects collisions across Google Calendar, Outlook, and Google Tasks`() {
        val items = buildTestItems()
        val summary = CollisionDetector.buildDaySummary(testDate, items, testZone)

        assertEquals(3, summary.collisionPairs.size)
        assertEquals(105, summary.totalOverlapMinutes)
        assertTrue(summary.collisionPairs.all { it.isCrossProvider })
    }

    @Test
    fun `completing a colliding Google Task resolves its collision`() {
        val start = testDate.atTime(10, 0).atZone(testZone).toInstant().toEpochMilli()
        val end = testDate.atTime(11, 0).atZone(testZone).toInstant().toEpochMilli()

        val outlookAppt = UnifiedTimelineItem(
            id = "outlook_1",
            title = "Outlook Client Meeting",
            startTimeMillis = start,
            endTimeMillis = end,
            source = ItemSource.OUTLOOK_CALENDAR,
            kind = ItemKind.APPOINTMENT
        )
        val googleTask = UnifiedTimelineItem(
            id = "gtask_1",
            title = "Prepare Slides",
            startTimeMillis = start + 15 * 60_000L,
            endTimeMillis = start + 45 * 60_000L,
            source = ItemSource.GOOGLE_TASKS,
            kind = ItemKind.GOOGLE_TASK,
            isCompleted = false
        )

        val activeResult = CollisionDetector.analyze(listOf(outlookAppt, googleTask))
        assertEquals(1, activeResult.collisionPairs.size)
        assertEquals(30, activeResult.totalOverlapMinutes)

        val completedResult = CollisionDetector.analyze(
            listOf(outlookAppt, googleTask.copy(isCompleted = true))
        )
        assertTrue(completedResult.collisionPairs.isEmpty())
        assertFalse(completedResult.annotatedItems.any { it.hasCollision })
    }

    @Test
    fun `findNextFreeSlot relocates colliding item to non-overlapping slot`() {
        val items = buildTestItems()
        val initialSummary = CollisionDetector.buildDaySummary(testDate, items, testZone)
        val collidingItem = initialSummary.collisionPairs.first().second

        val (newStart, newEnd) = CollisionDetector.findNextFreeSlot(
            dayItems = initialSummary.items,
            movingItemId = collidingItem.id,
            afterTimeMillis = collidingItem.startTimeMillis,
            durationMinutes = collidingItem.durationMinutes,
            zoneId = testZone
        )

        val updatedItems = initialSummary.items.map {
            if (it.id == collidingItem.id) it.copy(startTimeMillis = newStart, endTimeMillis = newEnd)
            else it
        }
        val reanalyzed = CollisionDetector.analyze(updatedItems)
        val movedAnnotated = reanalyzed.annotatedItems.first { it.id == collidingItem.id }

        assertFalse(movedAnnotated.hasCollision)
        assertEquals(2, reanalyzed.collisionPairs.size)
    }

    @Test
    fun `parses Outlook and Google ICS calendar events accurately`() {
        val icsSample = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:evt-1001@outlook.com
            SUMMARY:Quarterly Strategy Sync
            DESCRIPTION:Review OKRs and milestones
            LOCATION:Microsoft Teams
            DTSTART:20261108T140000Z
            DTEND:20261108T150000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = IcsParser.parseIcsContent(
            icsText = icsSample,
            source = ItemSource.OUTLOOK_CALENDAR,
            feedLabel = "Work Outlook",
            defaultZone = testZone
        )
        assertEquals(1, parsed.size)
        assertEquals("Quarterly Strategy Sync", parsed.first().title)
        assertEquals(ItemSource.OUTLOOK_CALENDAR, parsed.first().source)
        assertEquals(ItemKind.APPOINTMENT, parsed.first().kind)
        assertEquals(60, parsed.first().durationMinutes)
    }

    @Test
    fun `all-day events are not considered collisions with preexisting events`() {
        val dayStart = testDate.atStartOfDay(testZone).toInstant().toEpochMilli()
        val dayEnd = testDate.plusDays(1).atStartOfDay(testZone).toInstant().toEpochMilli()
        val meetingStart = testDate.atTime(14, 0).atZone(testZone).toInstant().toEpochMilli()
        val meetingEnd = testDate.atTime(15, 0).atZone(testZone).toInstant().toEpochMilli()

        val allDayEvent = UnifiedTimelineItem(
            id = "all_day_1",
            title = "Company Offsite",
            startTimeMillis = dayStart,
            endTimeMillis = dayEnd,
            isAllDay = true,
            source = ItemSource.GOOGLE_CALENDAR,
            kind = ItemKind.EVENT
        )
        val timedMeeting = UnifiedTimelineItem(
            id = "timed_1",
            title = "Client Sync",
            startTimeMillis = meetingStart,
            endTimeMillis = meetingEnd,
            isAllDay = false,
            source = ItemSource.OUTLOOK_CALENDAR,
            kind = ItemKind.APPOINTMENT
        )

        val result = CollisionDetector.analyze(listOf(timedMeeting, allDayEvent))
        assertTrue(result.collisionPairs.isEmpty())
        assertFalse(result.annotatedItems.any { it.hasCollision })
    }

    @Test
    fun `classifies Google and Outlook device calendars properly`() {
        assertEquals(
            ItemSource.GOOGLE_CALENDAR,
            DeviceCalendarReader.classifyCalendarSource("com.google", "alex@gmail.com", "Primary")
        )
        assertEquals(
            ItemSource.OUTLOOK_CALENDAR,
            DeviceCalendarReader.classifyCalendarSource("com.microsoft.office.outlook", "alex@company.com", "Calendar")
        )
    }
}
