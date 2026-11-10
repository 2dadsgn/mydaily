package com.mydaily.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.mydaily.app.MainActivity
import com.mydaily.app.R
import com.mydaily.app.data.ScheduleRepository
import com.mydaily.app.model.DayScheduleSummary
import com.mydaily.app.model.ItemKind
import com.mydaily.app.model.UnifiedTimelineItem
import com.mydaily.app.notification.CollisionNotificationManager
import com.mydaily.app.sync.ScheduleMonitorWorker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed class WidgetWeekRow {
    data class DayHeader(
        val date: LocalDate,
        val isToday: Boolean,
        val isTomorrow: Boolean,
        val eventCount: Int,
        val collisionCount: Int
    ) : WidgetWeekRow()

    data class EventItem(
        val date: LocalDate,
        val item: UnifiedTimelineItem
    ) : WidgetWeekRow()
}

class MyDailyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        CollisionNotificationManager.evaluateAndNotify(context)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH_WIDGET -> {
                CollisionNotificationManager.evaluateAndNotify(context, forceNotifyIfAny = true)
                refreshAllWidgets(context)
            }
            ACTION_AUTO_EXPIRE_WIDGET -> {
                CollisionNotificationManager.evaluateAndNotify(context)
                refreshAllWidgets(context)
            }
            ACTION_TOGGLE_COLLISIONS_FILTER -> {
                val repo = ScheduleRepository(context)
                repo.toggleWidgetCollisionsOnly()
                refreshAllWidgets(context)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.mydaily.app.widget.ACTION_REFRESH_WIDGET"
        const val ACTION_AUTO_EXPIRE_WIDGET = "com.mydaily.app.widget.ACTION_AUTO_EXPIRE_WIDGET"
        const val ACTION_TOGGLE_COLLISIONS_FILTER = "com.mydaily.app.widget.ACTION_TOGGLE_COLLISIONS_FILTER"

        private val dayColIds = intArrayOf(
            R.id.widget_day_col_0,
            R.id.widget_day_col_1,
            R.id.widget_day_col_2,
            R.id.widget_day_col_3,
            R.id.widget_day_col_4,
            R.id.widget_day_col_5,
            R.id.widget_day_col_6
        )
        private val dayNameIds = intArrayOf(
            R.id.widget_day_name_0,
            R.id.widget_day_name_1,
            R.id.widget_day_name_2,
            R.id.widget_day_name_3,
            R.id.widget_day_name_4,
            R.id.widget_day_name_5,
            R.id.widget_day_name_6
        )
        private val dayNumIds = intArrayOf(
            R.id.widget_day_num_0,
            R.id.widget_day_num_1,
            R.id.widget_day_num_2,
            R.id.widget_day_num_3,
            R.id.widget_day_num_4,
            R.id.widget_day_num_5,
            R.id.widget_day_num_6
        )
        private val dayDotIds = intArrayOf(
            R.id.widget_day_dot_0,
            R.id.widget_day_dot_1,
            R.id.widget_day_dot_2,
            R.id.widget_day_dot_3,
            R.id.widget_day_dot_4,
            R.id.widget_day_dot_5,
            R.id.widget_day_dot_6
        )

        fun buildWeekRows(
            weekSummaries: List<DayScheduleSummary>,
            today: LocalDate,
            collisionsOnly: Boolean
        ): List<WidgetWeekRow> {
            val rows = mutableListOf<WidgetWeekRow>()
            for (daySummary in weekSummaries) {
                val filteredItems = if (collisionsOnly) {
                    daySummary.items.filter { it.hasCollision }
                } else {
                    daySummary.items
                }

                // Show only days that have active events so past/empty days don't clutter the widget
                if (filteredItems.isEmpty()) continue

                rows.add(
                    WidgetWeekRow.DayHeader(
                        date = daySummary.date,
                        isToday = daySummary.date == today,
                        isTomorrow = daySummary.date == today.plusDays(1),
                        eventCount = daySummary.items.size,
                        collisionCount = daySummary.collisionPairs.size
                    )
                )
                for (item in filteredItems) {
                    rows.add(WidgetWeekRow.EventItem(date = daySummary.date, item = item))
                }
            }
            return rows
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val repo = ScheduleRepository(context)
            val today = LocalDate.now()
            val nowMillis = System.currentTimeMillis()
            val weekSummaries = repo.getWeekSummaries(today, days = 7, activeOnly = true, nowMillis = nowMillis)
            val overview = repo.getUpcomingCollisionsOverview(today, activeOnly = true, nowMillis = nowMillis)
            val todaySummary = overview.todaySummary
            val collisionsOnly = repo.isWidgetCollisionsOnly()

            // Schedule automatic widget refresh at the exact moment the next active event ends
            scheduleNextExpirationRefresh(context, repo, nowMillis)

            val views = RemoteViews(context.packageName, R.layout.widget_my_daily)
            val immutableFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            // 1. Header: Week Range & Summary
            val weekEnd = today.plusDays(6)
            val rangeFmt = DateTimeFormatter.ofPattern("MMM d")
            views.setTextViewText(
                R.id.widget_date_text,
                "${today.format(rangeFmt)} – ${weekEnd.format(rangeFmt)}"
            )

            val totalWeekEvents = weekSummaries.sumOf { it.items.size }
            val totalWeekConflicts = weekSummaries.sumOf { it.collisionPairs.size }
            val summaryText = if (totalWeekConflicts > 0) {
                "$totalWeekEvents event${if (totalWeekEvents == 1) "" else "s"} this week • $totalWeekConflicts conflict${if (totalWeekConflicts == 1) "" else "s"}"
            } else {
                "$totalWeekEvents event${if (totalWeekEvents == 1) "" else "s"} this week"
            }
            views.setTextViewText(R.id.widget_sources_summary, summaryText)

            // 2. Populate 7-Day Week Strip Bar (index 0 = TODAY clearly highlighted)
            val dayNameFmt = DateTimeFormatter.ofPattern("EEE")
            weekSummaries.take(7).forEachIndexed { index, daySum ->
                val isToday = index == 0
                val hasConflict = daySum.collisionPairs.isNotEmpty()
                val eventCount = daySum.items.size

                views.setTextViewText(
                    dayNameIds[index],
                    if (isToday) "TODAY" else daySum.date.format(dayNameFmt)
                )
                views.setTextViewText(dayNumIds[index], daySum.date.dayOfMonth.toString())

                val bgRes = when {
                    isToday -> R.drawable.widget_day_today_bg
                    hasConflict -> R.drawable.widget_collision_bg
                    else -> R.drawable.widget_card_bg
                }
                views.setInt(dayColIds[index], "setBackgroundResource", bgRes)

                when {
                    hasConflict -> {
                        views.setTextViewText(dayDotIds[index], "⚠ $eventCount")
                        views.setTextColor(
                            dayDotIds[index],
                            if (isToday) 0xFFFECACA.toInt() else 0xFFEF4444.toInt()
                        )
                    }
                    eventCount > 0 -> {
                        views.setTextViewText(dayDotIds[index], "• $eventCount")
                        views.setTextColor(
                            dayDotIds[index],
                            if (isToday) 0xFFFFFFFF.toInt() else 0xFF10B981.toInt()
                        )
                    }
                    else -> {
                        views.setTextViewText(dayDotIds[index], "·")
                        views.setTextColor(
                            dayDotIds[index],
                            if (isToday) 0xFFE2E8F0.toInt() else 0xFF64748B.toInt()
                        )
                    }
                }

                val dayClickIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(
                        CollisionNotificationManager.EXTRA_TARGET_DATE_EPOCH_DAY,
                        daySum.date.toEpochDay()
                    )
                }
                val dayPending = PendingIntent.getActivity(
                    context,
                    200 + index,
                    dayClickIntent,
                    immutableFlags
                )
                views.setOnClickPendingIntent(dayColIds[index], dayPending)
            }

            // 3. Today Conflict Banner (only visible when today has a conflict)
            if (todaySummary.collisionPairs.isNotEmpty()) {
                views.setViewVisibility(R.id.widget_collision_banner, View.VISIBLE)
                val firstPair = todaySummary.collisionPairs.first()
                views.setTextViewText(
                    R.id.widget_collision_banner_text,
                    "⚠ Today: ${firstPair.first.title} & ${firstPair.second.title}"
                )
            } else {
                views.setViewVisibility(R.id.widget_collision_banner, View.GONE)
            }

            // 4. Other-Day Conflict Badge & Banner
            if (overview.totalOtherDayCollisionsCount > 0 && overview.otherDaysWithCollisions.isNotEmpty()) {
                val nextConflictDay = overview.otherDaysWithCollisions.first()
                val nextDate = nextConflictDay.date
                val dayFmt = nextDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                val firstPair = nextConflictDay.collisionPairs.first()

                views.setViewVisibility(R.id.widget_other_day_badge, View.VISIBLE)
                views.setTextViewText(R.id.widget_other_day_badge, "⚠ $dayFmt")

                views.setViewVisibility(R.id.widget_future_collision_banner, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_future_collision_text,
                    "⚠ $dayFmt: ${firstPair.first.title} & ${firstPair.second.title}"
                )

                val openFutureDayIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(
                        CollisionNotificationManager.EXTRA_TARGET_DATE_EPOCH_DAY,
                        nextDate.toEpochDay()
                    )
                }
                val openFuturePending = PendingIntent.getActivity(
                    context,
                    100 + nextDate.toEpochDay().toInt(),
                    openFutureDayIntent,
                    immutableFlags
                )
                views.setOnClickPendingIntent(R.id.widget_future_collision_banner, openFuturePending)
                views.setOnClickPendingIntent(R.id.widget_other_day_badge, openFuturePending)
            } else {
                views.setViewVisibility(R.id.widget_other_day_badge, View.GONE)
                views.setViewVisibility(R.id.widget_future_collision_banner, View.GONE)
            }

            // 5. Filter button
            views.setTextViewText(
                R.id.widget_filter_button,
                if (collisionsOnly) "Conflicts" else "All"
            )

            // 6. Week Schedule List (all days + all events in the week)
            val weekRows = buildWeekRows(weekSummaries, today, collisionsOnly)
            views.setEmptyView(R.id.widget_events_list, R.id.widget_empty_view)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val builder = RemoteViews.RemoteCollectionItems.Builder()
                    .setHasStableIds(false)
                    .setViewTypeCount(2)

                weekRows.forEachIndexed { index, row ->
                    val rowView = when (row) {
                        is WidgetWeekRow.DayHeader -> buildDayHeaderRemoteViews(context, row)
                        is WidgetWeekRow.EventItem -> buildRowRemoteViews(context, row.item, row.date)
                    }
                    builder.addItem(index.toLong(), rowView)
                }
                views.setRemoteAdapter(R.id.widget_events_list, builder.build())
            } else {
                val serviceIntent = Intent(context, MyDailyWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME) + "&ts=${System.currentTimeMillis()}")
                }
                @Suppress("DEPRECATION")
                views.setRemoteAdapter(R.id.widget_events_list, serviceIntent)
            }

            // 7. Click handlers
            val openTodayIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(
                    CollisionNotificationManager.EXTRA_TARGET_DATE_EPOCH_DAY,
                    today.toEpochDay()
                )
            }
            val openTodayPending = PendingIntent.getActivity(
                context,
                0,
                openTodayIntent,
                immutableFlags
            )
            views.setOnClickPendingIntent(R.id.widget_header_container, openTodayPending)
            views.setOnClickPendingIntent(R.id.widget_collision_banner, openTodayPending)

            val itemClickTemplate = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val mutableFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val itemClickPendingIntent = PendingIntent.getActivity(
                context,
                1,
                itemClickTemplate,
                mutableFlags
            )
            views.setPendingIntentTemplate(R.id.widget_events_list, itemClickPendingIntent)

            val toggleFilterIntent = Intent(context, MyDailyWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_COLLISIONS_FILTER
            }
            val toggleFilterPending = PendingIntent.getBroadcast(
                context,
                2,
                toggleFilterIntent,
                immutableFlags
            )
            views.setOnClickPendingIntent(R.id.widget_filter_button, toggleFilterPending)

            val refreshIntent = Intent(context, MyDailyWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPending = PendingIntent.getBroadcast(
                context,
                3,
                refreshIntent,
                immutableFlags
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                @Suppress("DEPRECATION")
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_events_list)
            }
        }

        fun buildDayHeaderRemoteViews(
            context: Context,
            header: WidgetWeekRow.DayHeader
        ): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_day_header_row)
            val dateFmt = header.date.format(DateTimeFormatter.ofPattern("EEE, MMM d")).uppercase()
            val prefix = when {
                header.isToday -> "★ TODAY • "
                header.isTomorrow -> "TOMORROW • "
                else -> ""
            }
            rv.setTextViewText(R.id.widget_day_header_title, "$prefix$dateFmt")
            if (header.isToday) {
                rv.setInt(R.id.widget_day_header_root, "setBackgroundResource", R.drawable.widget_day_today_bg)
                rv.setTextColor(R.id.widget_day_header_title, 0xFFFFFFFF.toInt())
            } else {
                rv.setInt(R.id.widget_day_header_root, "setBackgroundResource", 0)
            }

            when {
                header.collisionCount > 0 -> {
                    rv.setTextViewText(
                        R.id.widget_day_header_count,
                        "⚠ ${header.collisionCount} conflict${if (header.collisionCount == 1) "" else "s"}"
                    )
                    rv.setTextColor(
                        R.id.widget_day_header_count,
                        if (header.isToday) 0xFFFECACA.toInt() else 0xFFEF4444.toInt()
                    )
                }
                header.eventCount > 0 -> {
                    rv.setTextViewText(
                        R.id.widget_day_header_count,
                        "${header.eventCount} event${if (header.eventCount == 1) "" else "s"}"
                    )
                    rv.setTextColor(
                        R.id.widget_day_header_count,
                        if (header.isToday) 0xFFFFFFFF.toInt() else 0xFF64748B.toInt()
                    )
                }
                else -> {
                    rv.setTextViewText(R.id.widget_day_header_count, "No events")
                    rv.setTextColor(
                        R.id.widget_day_header_count,
                        if (header.isToday) 0xFFE2E8F0.toInt() else 0xFF94A3B8.toInt()
                    )
                }
            }

            val fillInIntent = Intent().apply {
                putExtra(
                    CollisionNotificationManager.EXTRA_TARGET_DATE_EPOCH_DAY,
                    header.date.toEpochDay()
                )
            }
            rv.setOnClickFillInIntent(R.id.widget_day_header_root, fillInIntent)
            return rv
        }

        fun buildRowRemoteViews(
            context: Context,
            item: UnifiedTimelineItem,
            date: LocalDate
        ): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_item_row)

            val colorInt = item.source.colorHex.toInt()
            rv.setInt(R.id.widget_item_source_bar, "setBackgroundColor", colorInt)

            val zone = ZoneId.systemDefault()
            val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
            val startStr = if (item.isAllDay) {
                "All"
            } else {
                Instant.ofEpochMilli(item.startTimeMillis).atZone(zone).format(timeFmt)
            }
            val endStr = if (item.isAllDay) {
                "Day"
            } else {
                Instant.ofEpochMilli(item.endTimeMillis).atZone(zone).format(timeFmt)
            }

            rv.setTextViewText(R.id.widget_item_start_time, startStr)
            rv.setTextViewText(R.id.widget_item_end_time, endStr)

            val titleText = when {
                item.kind == ItemKind.GOOGLE_TASK && item.isCompleted -> "✓ ${item.title}"
                item.kind == ItemKind.GOOGLE_TASK -> "☐ ${item.title}"
                else -> item.title
            }
            rv.setTextViewText(R.id.widget_item_title, titleText)

            rv.setTextViewText(R.id.widget_item_badge, item.source.displayName)
            rv.setTextColor(R.id.widget_item_badge, colorInt)

            if (item.hasCollision) {
                rv.setViewVisibility(R.id.widget_item_collision_box, View.VISIBLE)
                val otherTitles = item.collisions.joinToString(", ") { it.otherTitle }
                rv.setTextViewText(R.id.widget_item_collision_text, "⚠ Overlaps with $otherTitles")
            } else {
                rv.setViewVisibility(R.id.widget_item_collision_box, View.GONE)
            }

            val fillInIntent = Intent().apply {
                putExtra("selected_item_id", item.id)
                putExtra(
                    CollisionNotificationManager.EXTRA_TARGET_DATE_EPOCH_DAY,
                    date.toEpochDay()
                )
            }
            rv.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent)
            return rv
        }

        private fun scheduleNextExpirationRefresh(
            context: Context,
            repo: ScheduleRepository,
            nowMillis: Long = System.currentTimeMillis()
        ) {
            val nextExpirationMillis = repo.getNextActiveExpirationMillis(nowMillis)
            val delayMillis = (nextExpirationMillis - nowMillis + 1_500L).coerceAtLeast(5_000L)

            // 1. Schedule via AlarmManager.setWindow (no special exact-alarm permission needed)
            runCatching {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                val expireIntent = Intent(context, MyDailyWidgetProvider::class.java).apply {
                    action = ACTION_AUTO_EXPIRE_WIDGET
                }
                val expirePending = PendingIntent.getBroadcast(
                    context,
                    901,
                    expireIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager?.setWindow(
                    AlarmManager.RTC,
                    nowMillis + delayMillis,
                    15_000L,
                    expirePending
                )
            }

            // 2. Also schedule via WorkManager so it reliably runs even under Doze/deferral
            ScheduleMonitorWorker.scheduleNextExpirationCheck(context, delayMillis)
        }

        fun refreshAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MyDailyWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (id in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, id)
            }
        }

        fun requestPinWidget(context: Context): Boolean {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            if (!appWidgetManager.isRequestPinAppWidgetSupported) return false
            val provider = ComponentName(context, MyDailyWidgetProvider::class.java)
            return appWidgetManager.requestPinAppWidget(provider, null, null)
        }
    }
}
