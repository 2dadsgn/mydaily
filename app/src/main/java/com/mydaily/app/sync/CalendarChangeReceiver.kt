package com.mydaily.app.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mydaily.app.notification.CollisionNotificationManager
import com.mydaily.app.widget.MyDailyWidgetProvider

/**
 * Receives system calendar provider changes (ACTION_PROVIDER_CHANGED on content://com.android.calendar),
 * date/time changes, and device boot events to immediately update the widget and send collision alerts.
 */
class CalendarChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        CollisionNotificationManager.evaluateAndNotify(context)
        MyDailyWidgetProvider.refreshAllWidgets(context)
        ScheduleMonitorWorker.startRoutineMonitoring(context)
    }
}

