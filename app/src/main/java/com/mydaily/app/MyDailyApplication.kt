package com.mydaily.app

import android.app.Application
import android.content.res.Configuration
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import com.mydaily.app.data.DeviceCalendarReader
import com.mydaily.app.notification.CollisionNotificationManager
import com.mydaily.app.sync.ScheduleMonitorWorker
import com.mydaily.app.widget.MyDailyWidgetProvider

class MyDailyApplication : Application() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var calendarObserverRegistered = false

    private val debounceRunnable = Runnable {
        CollisionNotificationManager.evaluateAndNotify(applicationContext)
        MyDailyWidgetProvider.refreshAllWidgets(applicationContext)
    }

    private val calendarObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            mainHandler.removeCallbacks(debounceRunnable)
            mainHandler.postDelayed(debounceRunnable, 500L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        CollisionNotificationManager.ensureChannelCreated(this)
        ScheduleMonitorWorker.startRoutineMonitoring(this)
        registerCalendarObserverIfPermitted()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        MyDailyWidgetProvider.refreshAllWidgets(applicationContext)
    }

    fun registerCalendarObserverIfPermitted() {
        if (calendarObserverRegistered) return
        if (!DeviceCalendarReader.hasCalendarPermission(this)) return
        runCatching {
            contentResolver.registerContentObserver(
                CalendarContract.CONTENT_URI,
                true,
                calendarObserver
            )
            contentResolver.registerContentObserver(
                CalendarContract.Events.CONTENT_URI,
                true,
                calendarObserver
            )
            calendarObserverRegistered = true
        }
    }
}

