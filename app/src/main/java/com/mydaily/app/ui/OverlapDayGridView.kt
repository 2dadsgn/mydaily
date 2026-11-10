package com.mydaily.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mydaily.app.model.DayScheduleSummary
import com.mydaily.app.model.ItemKind
import com.mydaily.app.model.UnifiedTimelineItem
import com.mydaily.app.ui.theme.CollisionCrimson
import com.mydaily.app.ui.theme.ConflictBannerBackground
import com.mydaily.app.ui.theme.ConflictTextPrimary
import com.mydaily.app.ui.theme.SlateSurface
import com.mydaily.app.ui.theme.TextPrimary
import com.mydaily.app.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId

private data class PositionedBlock(
    val item: UnifiedTimelineItem,
    val startMinutesFromDayStart: Int,
    val durationMinutes: Int,
    val columnIndex: Int,
    val totalColumns: Int
)

@Composable
fun OverlapDayGridView(
    daySummary: DayScheduleSummary,
    onAutoResolve: (UnifiedTimelineItem) -> Unit,
    onToggleTaskDone: (UnifiedTimelineItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val startHour = 8
    val endHour = 20
    val hourHeightDp = 76.dp
    val zone = ZoneId.systemDefault()

    val timedItems = daySummary.items.filter { !it.isAllDay }
    val positionedBlocks = computeColumnLayout(timedItems, startHour, zone)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Legend Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateSurface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Visual Day Overlap Map (08:00 – 20:00)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Events, Outlook Appointments, and Google Tasks that collide in time are placed side-by-side with red ⚡ COLLISION ZONES marking the exact overlap minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val totalHours = endHour - startHour + 1
        val gridHeight: Dp = hourHeightDp * totalHours

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(SlateSurface)
                .padding(vertical = 8.dp)
        ) {
            // 1. Background Hour Grid Lines
            for (h in startHour..endHour) {
                val topOffset = hourHeightDp * (h - startHour)
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = topOffset)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = String.format("%02d:00", h),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(44.dp)
                    )
                    HorizontalDivider(
                        color = Color(0x3394A3B8),
                        thickness = 1.dp,
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .weight(1f)
                    )
                }
            }

            // 2. Collision Zone Highlight Bands (exact overlap windows)
            for (pair in daySummary.collisionPairs) {
                val overlapStartMin = minutesFromStartHour(pair.overlapStartMillis, startHour, zone)
                val overlapEndMin = minutesFromStartHour(pair.overlapEndMillis, startHour, zone)
                val durationMin = (overlapEndMin - overlapStartMin).coerceAtLeast(10)
                if (overlapEndMin > 0 && overlapStartMin < totalHours * 60) {
                    val yOffset = hourHeightDp * (overlapStartMin / 60f)
                    val bandHeight = hourHeightDp * (durationMin / 60f)

                    Box(
                        modifier = Modifier
                            .padding(start = 54.dp, end = 8.dp)
                            .fillMaxWidth()
                            .offset(y = yOffset)
                            .height(bandHeight)
                            .background(
                                color = Color(0x38EF4444),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(
                                width = 1.5.dp,
                                color = CollisionCrimson,
                                shape = RoundedCornerShape(6.dp)
                            )
                    ) {
                        Surface(
                            color = CollisionCrimson,
                            shape = RoundedCornerShape(bottomEnd = 6.dp, topStart = 4.dp),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Text(
                                text = "⚡ COLLISION (${pair.overlapMinutes}m)",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // 3. Positioned Event / Appointment / Task Blocks
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 56.dp, end = 10.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Render blocks using fractional width alignment
                }
                for (block in positionedBlocks) {
                    val yOffset = hourHeightDp * (block.startMinutesFromDayStart / 60f)
                    val blockHeight = (hourHeightDp * (block.durationMinutes / 60f)).coerceAtLeast(36.dp)
                    val sourceColor = Color(block.item.source.colorHex)
                    val isColliding = block.item.hasCollision

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = yOffset)
                            .height(blockHeight)
                    ) {
                        if (block.columnIndex > 0) {
                            Spacer(modifier = Modifier.weight(block.columnIndex.toFloat()))
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(blockHeight)
                                .padding(horizontal = 2.dp, vertical = 1.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isColliding) sourceColor.copy(alpha = 0.28f)
                                    else sourceColor.copy(alpha = 0.20f)
                                )
                                .border(
                                    width = if (isColliding) 2.dp else 1.dp,
                                    color = if (isColliding) CollisionCrimson else sourceColor,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (block.item.kind == ItemKind.GOOGLE_TASK) {
                                        onToggleTaskDone(block.item)
                                    }
                                }
                                .padding(6.dp)
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${block.item.source.shortBadge} • ${block.item.formattedTimeRange()}",
                                        color = if (isColliding) ConflictTextPrimary else sourceColor,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = block.item.title,
                                    color = TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isColliding && blockHeight >= 52.dp) {
                                    val firstConflict = block.item.collisions.first()
                                    Text(
                                        text = "⚡ Overlaps (${firstConflict.overlapMinutes}m) with ${firstConflict.otherTitle}",
                                        color = ConflictTextPrimary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        val remainingCols = block.totalColumns - block.columnIndex - 1
                        if (remainingCols > 0) {
                            Spacer(modifier = Modifier.weight(remainingCols.toFloat()))
                        }
                    }
                }
            }
        }

        // Overlap summary list at bottom of grid
        if (daySummary.collisionPairs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = ConflictBannerBackground),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "⚡ Overlapping Events on This Day",
                        color = ConflictTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    for (pair in daySummary.collisionPairs) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "${pair.first.title} (${pair.first.source.shortBadge}) ⚡ ${pair.second.title} (${pair.second.source.shortBadge})",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Overlap window: ${pair.formattedOverlapWindow()}",
                                color = ConflictTextPrimary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

private fun minutesFromStartHour(
    epochMillis: Long,
    startHour: Int,
    zoneId: ZoneId
): Int {
    val zoned = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
    val minutesOfDay = zoned.hour * 60 + zoned.minute
    return (minutesOfDay - startHour * 60).coerceAtLeast(0)
}

private fun computeColumnLayout(
    items: List<UnifiedTimelineItem>,
    startHour: Int,
    zoneId: ZoneId
): List<PositionedBlock> {
    if (items.isEmpty()) return emptyList()
    val sorted = items.sortedBy { it.startTimeMillis }
    val result = mutableListOf<PositionedBlock>()

    // Group overlapping clusters
    val clusters = mutableListOf<MutableList<UnifiedTimelineItem>>()
    var currentCluster = mutableListOf<UnifiedTimelineItem>()
    var clusterEndMillis = Long.MIN_VALUE

    for (item in sorted) {
        if (currentCluster.isEmpty() || item.startTimeMillis < clusterEndMillis) {
            currentCluster.add(item)
            if (item.endTimeMillis > clusterEndMillis) {
                clusterEndMillis = item.endTimeMillis
            }
        } else {
            clusters.add(currentCluster)
            currentCluster = mutableListOf(item)
            clusterEndMillis = item.endTimeMillis
        }
    }
    if (currentCluster.isNotEmpty()) {
        clusters.add(currentCluster)
    }

    for (cluster in clusters) {
        val totalCols = cluster.size.coerceAtMost(3)
        cluster.forEachIndexed { idx, item ->
            val startMin = minutesFromStartHour(item.startTimeMillis, startHour, zoneId)
            result.add(
                PositionedBlock(
                    item = item,
                    startMinutesFromDayStart = startMin,
                    durationMinutes = item.durationMinutes,
                    columnIndex = idx % totalCols,
                    totalColumns = totalCols
                )
            )
        }
    }

    return result
}

