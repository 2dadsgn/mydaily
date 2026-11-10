package com.mydaily.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mydaily.app.domain.CollisionDetector
import com.mydaily.app.model.ItemKind
import com.mydaily.app.model.ItemSource
import com.mydaily.app.model.UnifiedTimelineItem
import com.mydaily.app.ui.theme.ClearEmerald
import com.mydaily.app.ui.theme.CollisionCrimson
import com.mydaily.app.ui.theme.ConflictTextPrimary
import com.mydaily.app.ui.theme.SlateSurface
import com.mydaily.app.ui.theme.TextPrimary
import com.mydaily.app.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddScheduleItemDialog(
    selectedDate: LocalDate,
    existingDayItems: List<UnifiedTimelineItem>,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        subtitle: String,
        location: String,
        description: String,
        source: ItemSource,
        kind: ItemKind,
        startHour: Int,
        startMinute: Int,
        durationMinutes: Int
    ) -> Unit
) {
    var selectedSource by remember { mutableStateOf(ItemSource.GOOGLE_CALENDAR) }
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startHour by remember { mutableIntStateOf(9) }
    var startMinute by remember { mutableIntStateOf(30) }
    var durationMinutes by remember { mutableIntStateOf(30) }

    val kind = when (selectedSource) {
        ItemSource.GOOGLE_CALENDAR -> ItemKind.EVENT
        ItemSource.OUTLOOK_CALENDAR -> ItemKind.APPOINTMENT
        ItemSource.GOOGLE_TASKS -> ItemKind.GOOGLE_TASK
        ItemSource.DEVICE_CALENDAR -> ItemKind.EVENT
    }

    // Live collision check for the candidate time slot
    val zone = ZoneId.systemDefault()
    val candidateStart = selectedDate.atTime(startHour, startMinute).atZone(zone).toInstant().toEpochMilli()
    val candidateEnd = candidateStart + durationMinutes * 60_000L
    val candidateItem = UnifiedTimelineItem(
        id = "preview_candidate",
        title = title.ifBlank { "New ${kind.label}" },
        startTimeMillis = candidateStart,
        endTimeMillis = candidateEnd,
        source = selectedSource,
        kind = kind
    )
    val previewCollisions = remember(selectedSource, startHour, startMinute, durationMinutes, existingDayItems) {
        CollisionDetector.analyze(existingDayItems + candidateItem)
            .annotatedItems
            .firstOrNull { it.id == "preview_candidate" }
            ?.collisions
            .orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        title = {
            Text(
                text = "Add Event, Appointment or Google Task",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Select Provider & Type:",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = selectedSource == ItemSource.GOOGLE_CALENDAR,
                        onClick = { selectedSource = ItemSource.GOOGLE_CALENDAR },
                        label = { Text("Google Cal Event", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedSource == ItemSource.OUTLOOK_CALENDAR,
                        onClick = { selectedSource = ItemSource.OUTLOOK_CALENDAR },
                        label = { Text("Outlook Appt", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedSource == ItemSource.GOOGLE_TASKS,
                        onClick = { selectedSource = ItemSource.GOOGLE_TASKS },
                        label = { Text("Google Task", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (e.g. Design Sync / Submit Report)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = {
                        Text(
                            if (selectedSource == ItemSource.GOOGLE_TASKS) "Task List (e.g. Work Tasks)"
                            else "Location / Meeting Link (e.g. Meet / Teams)"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Start Time: ${String.format("%02d:%02d", startHour, startMinute)}",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Quick hour selector pills
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(9, 10, 11, 13, 14, 15, 16, 17).forEach { hr ->
                        FilterChip(
                            selected = startHour == hr,
                            onClick = { startHour = hr },
                            label = { Text(String.format("%02d:00", hr), fontSize = 11.sp) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 15, 30, 45).forEach { min ->
                        FilterChip(
                            selected = startMinute == min,
                            onClick = { startMinute = min },
                            label = { Text(String.format(":%02d", min), fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Duration: $durationMinutes min",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 45, 60).forEach { dur ->
                        FilterChip(
                            selected = durationMinutes == dur,
                            onClick = { durationMinutes = dur },
                            label = { Text("${dur}m", fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Pre-Save Collision Preview Box
                if (previewCollisions.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x33EF4444))
                            .border(1.dp, CollisionCrimson, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "⚡ Live Collision Alert (${previewCollisions.size} conflict${if (previewCollisions.size > 1) "s" else ""}):",
                            color = Color(0xFFFCA5A5),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        previewCollisions.forEach { c ->
                            Text(
                                text = "• Overlaps ${c.overlapMinutes}m with '${c.otherTitle}' (${c.otherSource.displayName})",
                                color = Color(0xFFFECACA),
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x2210B981))
                            .border(1.dp, ClearEmerald, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "✓ No schedule collisions in this time slot!",
                            color = Color(0xFF6EE7B7),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        title.ifBlank { "New ${kind.label}" },
                        "${selectedSource.displayName} • Custom",
                        location,
                        description,
                        selectedSource,
                        kind,
                        startHour,
                        startMinute,
                        durationMinutes
                    )
                }
            ) {
                Text("Save to Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

