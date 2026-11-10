package com.mydaily.app.data

import com.mydaily.app.model.ItemKind
import com.mydaily.app.model.ItemSource
import com.mydaily.app.model.UnifiedTimelineItem
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Parses iCalendar (.ics) feeds from Outlook Calendar ("Publish a calendar" ICS link)
 * or Google Calendar ("Secret address in iCal format").
 */
object IcsParser {

    fun fetchAndParseIcsUrl(
        urlString: String,
        source: ItemSource,
        feedLabel: String
    ): List<UnifiedTimelineItem> {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "MyDaily-Android/1.0")
        }
        return try {
            val content = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            parseIcsContent(content, source, feedLabel)
        } finally {
            connection.disconnect()
        }
    }

    fun parseIcsContent(
        icsText: String,
        source: ItemSource,
        feedLabel: String,
        defaultZone: ZoneId = ZoneId.systemDefault()
    ): List<UnifiedTimelineItem> {
        val unfoldedLines = unfoldIcsLines(icsText)
        val items = mutableListOf<UnifiedTimelineItem>()

        var inEvent = false
        var uid = ""
        var summary = ""
        var description = ""
        var location = ""
        var dtStartRaw = ""
        var dtEndRaw = ""

        for (line in unfoldedLines) {
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    inEvent = true
                    uid = ""
                    summary = ""
                    description = ""
                    location = ""
                    dtStartRaw = ""
                    dtEndRaw = ""
                }
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    if (inEvent && dtStartRaw.isNotBlank()) {
                        val (startMillis, isAllDay) = parseIcsDateTime(dtStartRaw, defaultZone)
                        val endMillis = if (dtEndRaw.isNotBlank()) {
                            parseIcsDateTime(dtEndRaw, defaultZone).first
                        } else {
                            startMillis + if (isAllDay) 86_400_000L else 3_600_000L
                        }
                        if (startMillis > 0L) {
                            val kind = if (source == ItemSource.OUTLOOK_CALENDAR) {
                                ItemKind.APPOINTMENT
                            } else {
                                ItemKind.EVENT
                            }
                            items.add(
                                UnifiedTimelineItem(
                                    id = "ics_${uid.ifBlank { UUID.randomUUID().toString() }}_$startMillis",
                                    title = summary.ifBlank { "(Untitled Event)" },
                                    subtitle = feedLabel,
                                    description = description.replace("\\n", "\n").replace("\\,", ","),
                                    location = location.replace("\\,", ","),
                                    startTimeMillis = startMillis,
                                    endTimeMillis = endMillis.coerceAtLeast(startMillis + 15 * 60_000L),
                                    isAllDay = isAllDay,
                                    source = source,
                                    kind = kind,
                                    isEditable = false
                                )
                            )
                        }
                    }
                    inEvent = false
                }
                inEvent -> {
                    val colonIndex = line.indexOf(':')
                    if (colonIndex > 0) {
                        val keyPart = line.substring(0, colonIndex).uppercase()
                        val valuePart = line.substring(colonIndex + 1).trim()
                        when {
                            keyPart == "UID" -> uid = valuePart
                            keyPart.startsWith("SUMMARY") -> summary = valuePart
                            keyPart.startsWith("DESCRIPTION") -> description = valuePart
                            keyPart.startsWith("LOCATION") -> location = valuePart
                            keyPart.startsWith("DTSTART") -> dtStartRaw = valuePart
                            keyPart.startsWith("DTEND") -> dtEndRaw = valuePart
                        }
                    }
                }
            }
        }

        return items
    }

    private fun unfoldIcsLines(icsText: String): List<String> {
        val rawLines = icsText.replace("\r\n", "\n").split("\n")
        val unfolded = mutableListOf<String>()
        for (line in rawLines) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && unfolded.isNotEmpty()) {
                unfolded[unfolded.lastIndex] = unfolded.last() + line.substring(1)
            } else {
                unfolded.add(line.trimEnd())
            }
        }
        return unfolded
    }

    private fun parseIcsDateTime(raw: String, zoneId: ZoneId): Pair<Long, Boolean> {
        val clean = raw.trim()
        return try {
            when {
                clean.length == 8 && clean.all { it.isDigit() } -> {
                    val date = LocalDate.parse(clean, DateTimeFormatter.BASIC_ISO_DATE)
                    date.atStartOfDay(zoneId).toInstant().toEpochMilli() to true
                }
                clean.endsWith("Z", ignoreCase = true) -> {
                    val core = clean.removeSuffix("Z").removeSuffix("z")
                    val dt = LocalDateTime.parse(core, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                    dt.toInstant(ZoneOffset.UTC).toEpochMilli() to false
                }
                clean.contains("T") -> {
                    val pattern = if (clean.length >= 15) "yyyyMMdd'T'HHmmss" else "yyyyMMdd'T'HHmm"
                    val dt = LocalDateTime.parse(clean.take(pattern.length - 2), DateTimeFormatter.ofPattern(pattern))
                    dt.atZone(zoneId).toInstant().toEpochMilli() to false
                }
                else -> 0L to false
            }
        } catch (_: Exception) {
            0L to false
        }
    }
}

