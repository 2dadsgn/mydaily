package com.mydaily.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mydaily.app.MainActivity
import com.mydaily.app.R
import com.mydaily.app.data.ScheduleRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object CollisionNotificationManager {

    const val CHANNEL_ID = "mydaily_collision_alerts"
    private const val NOTIFICATION_ID = 4001
    const val EXTRA_TARGET_DATE_EPOCH_DAY = "extra_target_date_epoch_day"

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannelCreated(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Schedule Conflicts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when events or tasks overlap."
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun evaluateAndNotify(
        context: Context,
        forceNotifyIfAny: Boolean = false
    ) {
        ensureChannelCreated(context)
        val repo = ScheduleRepository(context)
        val overview = repo.getUpcomingCollisionsOverview(activeOnly = true)
        val allCollisions = overview.allCollisionsByDate

        val notificationManager = NotificationManagerCompat.from(context)

        if (allCollisions.isEmpty()) {
            repo.saveNotifiedCollisionKeys(emptySet())
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }

        val currentKeys = allCollisions.map { (date, pair) ->
            "${date}|${pair.first.title.trim().lowercase()}|${pair.second.title.trim().lowercase()}|${pair.overlapStartMillis}"
        }.toSet()

        val previousKeys = repo.getNotifiedCollisionKeys()
        val newlyDetectedKeys = currentKeys - previousKeys

        if (newlyDetectedKeys.isEmpty() && !forceNotifyIfAny) {
            return
        }

        repo.saveNotifiedCollisionKeys(currentKeys)

        if (!hasNotificationPermission(context)) {
            return
        }

        val today = LocalDate.now()
        val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
        val (firstDate, firstPair) = allCollisions.first()
        val dayLabel = if (firstDate == today) "Today" else firstDate.format(dateFmt)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_DATE_EPOCH_DAY, firstDate.toEpochDay())
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            firstDate.toEpochDay().toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (allCollisions.size == 1) {
            "Schedule Conflict ($dayLabel)"
        } else {
            "${allCollisions.size} Schedule Conflicts"
        }

        val bodyText = "${firstPair.first.title} overlaps with ${firstPair.second.title}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_calendar)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
}
