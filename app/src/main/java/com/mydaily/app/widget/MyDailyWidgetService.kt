package com.mydaily.app.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.mydaily.app.data.ScheduleRepository
import java.time.LocalDate

class MyDailyWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return MyDailyRemoteViewsFactory(applicationContext)
    }
}

class MyDailyRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var rows: List<WidgetWeekRow> = emptyList()

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    private fun loadData() {
        val repo = ScheduleRepository(context)
        val today = LocalDate.now()
        val weekSummaries = repo.getWeekSummaries(today, days = 7, activeOnly = true)
        val collisionsOnly = repo.isWidgetCollisionsOnly()
        rows = MyDailyWidgetProvider.buildWeekRows(weekSummaries, today, collisionsOnly)
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position < 0 || position >= rows.size) return null
        return when (val row = rows[position]) {
            is WidgetWeekRow.DayHeader -> MyDailyWidgetProvider.buildDayHeaderRemoteViews(context, row)
            is WidgetWeekRow.EventItem -> MyDailyWidgetProvider.buildRowRemoteViews(context, row.item, row.date)
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
