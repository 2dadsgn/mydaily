package com.mydaily.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mydaily.app.model.ItemSource
import com.mydaily.app.ui.theme.ClearEmerald
import com.mydaily.app.ui.theme.CollisionCrimson
import com.mydaily.app.ui.theme.GoogleCalendarBlue
import com.mydaily.app.ui.theme.GoogleTasksGreen
import com.mydaily.app.ui.theme.OutlookBlue
import com.mydaily.app.ui.theme.SlateSurface
import com.mydaily.app.ui.theme.SlateSurfaceVariant
import com.mydaily.app.ui.theme.TextPrimary
import com.mydaily.app.ui.theme.TextSecondary
import com.mydaily.app.widget.MyDailyWidgetProvider

fun openOutlookOrAccountSettings(context: Context) {
    val outlookIntent = context.packageManager.getLaunchIntentForPackage("com.microsoft.office.outlook")
    if (outlookIntent != null) {
        Toast.makeText(
            context,
            "In Outlook: tap Profile (top-left) -> Settings (gear) -> select your account -> turn ON 'Sync calendars'",
            Toast.LENGTH_LONG
        ).show()
        context.startActivity(outlookIntent)
    } else {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_ADD_ACCOUNT))
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=com.microsoft.office.outlook")
                    )
                )
            }
        }
    }
}

@Composable
fun ConnectionsAndWidgetTab(
    uiState: MyDailyUiState,
    onRequestCalendarPermission: () -> Unit,
    onConnectGoogleTasksAccount: () -> Unit,
    onToggleAccount: (String) -> Unit,
    onAddIcsFeed: (String, String, ItemSource) -> Unit,
    onRemoveIcsFeed: (String) -> Unit,
    onSaveGoogleTasksToken: (String) -> Unit,
    onResetRescheduledTimes: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showIcsSection by remember { mutableStateOf(false) }
    var feedLabel by remember { mutableStateOf("") }
    var feedUrl by remember { mutableStateOf("") }

    val googleCalendars = uiState.connectedAccounts.filter {
        it.id.startsWith("dev_cal_") && it.source == ItemSource.GOOGLE_CALENDAR
    }
    val outlookCalendars = uiState.connectedAccounts.filter {
        it.id.startsWith("dev_cal_") && it.source == ItemSource.OUTLOOK_CALENDAR
    }
    val otherCalendars = uiState.connectedAccounts.filter {
        it.id.startsWith("dev_cal_") && it.source == ItemSource.DEVICE_CALENDAR
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. GOOGLE CALENDAR CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateSurface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = GoogleCalendarBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Calendar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (googleCalendars.isNotEmpty()) {
                                "${googleCalendars.size} calendar(s) synced from your phone"
                            } else {
                                "Syncs automatically with your phone's Google account"
                            },
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    if (googleCalendars.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Connected",
                            tint = ClearEmerald
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!uiState.hasCalendarPermission) {
                    Button(
                        onClick = onRequestCalendarPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = GoogleCalendarBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect Google Calendar", fontWeight = FontWeight.Bold)
                    }
                } else if (googleCalendars.isEmpty()) {
                    OutlinedButton(
                        onClick = {
                            runCatching { context.startActivity(Intent(Settings.ACTION_ADD_ACCOUNT)) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add Google Account in Phone Settings")
                    }
                } else {
                    googleCalendars.forEach { cal ->
                        CalendarToggleRow(
                            name = cal.name,
                            subtitle = cal.accountName,
                            color = GoogleCalendarBlue,
                            enabled = cal.isEnabled,
                            onToggle = { onToggleAccount(cal.id) }
                        )
                    }
                }
            }
        }

        // 2. OUTLOOK CALENDAR CARD (Simplified 2-Step Guide)
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateSurface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = OutlookBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Outlook Calendar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (outlookCalendars.isNotEmpty() || uiState.icsFeeds.isNotEmpty()) {
                                "Connected to Outlook"
                            } else {
                                "Connect via the Outlook app or your phone's accounts"
                            },
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    if (outlookCalendars.isNotEmpty() || uiState.icsFeeds.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Connected",
                            tint = ClearEmerald
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (outlookCalendars.isNotEmpty()) {
                    outlookCalendars.forEach { cal ->
                        CalendarToggleRow(
                            name = cal.name,
                            subtitle = cal.accountName,
                            color = OutlookBlue,
                            enabled = cal.isEnabled,
                            onToggle = { onToggleAccount(cal.id) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Simple explanation box for Outlook on Android
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateSurfaceVariant.copy(alpha = 0.45f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Easiest Way (takes 10 seconds):",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "1. Open the Outlook app on your phone\n" +
                            "2. Tap your Profile icon (top-left) → Settings (⚙)\n" +
                            "3. Tap your Outlook account → turn ON 'Sync calendars'",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { openOutlookOrAccountSettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = OutlookBlue),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Outlook App", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                runCatching { context.startActivity(Intent(Settings.ACTION_ADD_ACCOUNT)) }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Phone Accounts", fontSize = 12.sp)
                        }
                    }
                }

                // Optional Web Link (.ics) tucked away cleanly
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = { showIcsSection = !showIcsSection }
                ) {
                    Text(
                        text = if (showIcsSection) "Hide web calendar link (.ics)" else "Work blocks device sync? Use an Outlook web link (.ics) instead",
                        fontSize = 11.5.sp,
                        color = Color(0xFF38BDF8)
                    )
                }

                AnimatedVisibility(visible = showIcsSection || uiState.icsFeeds.isNotEmpty()) {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        OutlinedTextField(
                            value = feedLabel,
                            onValueChange = { feedLabel = it },
                            label = { Text("Label (e.g. Work Outlook)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = feedUrl,
                            onValueChange = { feedUrl = it },
                            label = { Text("Paste Outlook .ics URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (feedUrl.isNotBlank()) {
                                    onAddIcsFeed(feedLabel.ifBlank { "Outlook Calendar" }, feedUrl, ItemSource.OUTLOOK_CALENDAR)
                                    feedLabel = ""
                                    feedUrl = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OutlookBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Connect .ics Link", fontWeight = FontWeight.Bold)
                        }

                        uiState.icsFeeds.forEach { feed ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                            ) {
                                Text(
                                    text = "${feed.label} (${feed.eventCount} events)",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onRemoveIcsFeed(feed.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = CollisionCrimson)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. GOOGLE TASKS CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateSurface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TaskAlt,
                        contentDescription = null,
                        tint = GoogleTasksGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Tasks",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (uiState.googleAccountEmail.isNotBlank()) {
                                "Signed in as ${uiState.googleAccountEmail}"
                            } else {
                                "Sign in with your Google account to sync tasks"
                            },
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    if (uiState.googleAccountEmail.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Connected",
                            tint = ClearEmerald
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onConnectGoogleTasksAccount,
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleTasksGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.googleAccountEmail.isNotBlank()) "Re-Sync / Switch Google Account" else "Connect Google Tasks",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Other device calendars if present
        if (otherCalendars.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Other Phone Calendars",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    otherCalendars.forEach { cal ->
                        CalendarToggleRow(
                            name = cal.name,
                            subtitle = cal.accountName,
                            color = Color(cal.source.colorHex),
                            enabled = cal.isEnabled,
                            onToggle = { onToggleAccount(cal.id) }
                        )
                    }
                }
            }
        }

        // 4. HOME SCREEN WIDGET CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateSurface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Widgets,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Home Screen Widget",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "See all your events, tasks, and collisions directly on your home screen.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val pinned = MyDailyWidgetProvider.requestPinWidget(context)
                        if (!pinned) {
                            Toast.makeText(
                                context,
                                "Long-press your Home Screen -> Widgets -> MyDaily to add the widget.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleCalendarBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Widget to Home Screen", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun CalendarToggleRow(
    name: String,
    subtitle: String,
    color: Color,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SlateSurfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { onToggle() }
        )
    }
}
