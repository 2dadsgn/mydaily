package com.mydaily.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mydaily.app.model.ItemSource
import com.mydaily.app.ui.theme.ClearEmerald
import com.mydaily.app.ui.theme.GoogleCalendarBlue
import com.mydaily.app.ui.theme.GoogleTasksGreen
import com.mydaily.app.ui.theme.OutlookBlue
import com.mydaily.app.ui.theme.SlateSurface
import com.mydaily.app.ui.theme.SlateSurfaceVariant
import com.mydaily.app.ui.theme.TextPrimary
import com.mydaily.app.ui.theme.TextSecondary

@Composable
fun ConnectCalendarsDialog(
    uiState: MyDailyUiState,
    onRequestCalendarPermission: () -> Unit,
    onConnectGoogleTasksAccount: () -> Unit,
    onAddIcsFeed: (String, String, ItemSource) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val deviceCalendarCount = uiState.connectedAccounts.count { it.id.startsWith("dev_cal_") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        title = {
            Text(
                text = "Welcome to MyDaily",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Connect your accounts in 3 quick taps:",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                // 1. Phone Calendars (Google & Outlook)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SlateSurfaceVariant.copy(alpha = 0.5f))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.hasCalendarPermission) Icons.Default.CheckCircle else Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = if (uiState.hasCalendarPermission) ClearEmerald else GoogleCalendarBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.hasCalendarPermission) {
                                "Google Calendar Connected ($deviceCalendarCount)"
                            } else {
                                "1. Connect Google Calendar"
                            },
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                    if (!uiState.hasCalendarPermission) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onRequestCalendarPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = GoogleCalendarBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Allow Calendar Access", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 2. Outlook Calendar (1-tap Open Outlook to enable Sync)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SlateSurfaceVariant.copy(alpha = 0.5f))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = OutlookBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "2. Connect Outlook Calendar",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "In the Outlook app: tap Profile → Settings (⚙) → your account → turn ON 'Sync calendars'.",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { openOutlookOrAccountSettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = OutlookBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Outlook to Enable Sync", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // 3. Google Tasks
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SlateSurfaceVariant.copy(alpha = 0.5f))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.googleAccountEmail.isNotBlank()) Icons.Default.CheckCircle else Icons.Default.TaskAlt,
                            contentDescription = null,
                            tint = if (uiState.googleAccountEmail.isNotBlank()) ClearEmerald else GoogleTasksGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.googleAccountEmail.isNotBlank()) {
                                "Google Tasks: ${uiState.googleAccountEmail}"
                            } else {
                                "3. Connect Google Tasks"
                            },
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onConnectGoogleTasksAccount,
                        colors = ButtonDefaults.buttonColors(containerColor = GoogleTasksGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.googleAccountEmail.isNotBlank()) "Switch Google Account" else "Sign in with Google",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Continue to App", fontWeight = FontWeight.Bold)
            }
        }
    )
}
