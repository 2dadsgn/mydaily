package com.mydaily.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mydaily.app.MyDailyApplication
import com.mydaily.app.data.DeviceCalendarReader
import com.mydaily.app.data.ScheduleRepository
import com.mydaily.app.domain.CollisionDetector
import com.mydaily.app.model.ConnectedCalendarAccount
import com.mydaily.app.model.DayScheduleSummary
import com.mydaily.app.model.IcsFeedConfig
import com.mydaily.app.model.ItemKind
import com.mydaily.app.model.ItemSource
import com.mydaily.app.model.UnifiedTimelineItem
import com.mydaily.app.notification.CollisionNotificationManager
import com.mydaily.app.sync.ScheduleMonitorWorker
import com.mydaily.app.widget.MyDailyWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class SourceFilter(val label: String) {
    ALL("All"),
    COLLISIONS_ONLY("⚡ Collisions"),
    GOOGLE_CALENDAR("Google Cal"),
    OUTLOOK_CALENDAR("Outlook"),
    GOOGLE_TASKS("Google Tasks")
}

enum class AppTab(val label: String) {
    TIMELINE("Schedule"),
    OVERLAP_GRID("Day Grid"),
    CONNECTIONS_WIDGET("Calendars & Widget")
}

data class MyDailyUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedFilter: SourceFilter = SourceFilter.ALL,
    val activeTab: AppTab = AppTab.TIMELINE,
    val daySummary: DayScheduleSummary = DayScheduleSummary(
        date = LocalDate.now(),
        items = emptyList(),
        collisionPairs = emptyList(),
        totalOverlapMinutes = 0,
        googleCalendarCount = 0,
        outlookCalendarCount = 0,
        googleTasksCount = 0
    ),
    val allTimelineItems: List<UnifiedTimelineItem> = emptyList(),
    val weekSummaries: List<DayScheduleSummary> = emptyList(),
    val otherDaysWithCollisions: List<DayScheduleSummary> = emptyList(),
    val connectedAccounts: List<ConnectedCalendarAccount> = emptyList(),
    val icsFeeds: List<IcsFeedConfig> = emptyList(),
    val googleTasksToken: String = "",
    val googleAccountEmail: String = "",
    val hasCalendarPermission: Boolean = false,
    val hasAnyConnectedSource: Boolean = false,
    val showConnectPromptDialog: Boolean = false,
    val isSyncing: Boolean = false,
    val statusBannerMessage: String? = null
)

class MyDailyViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ScheduleRepository(application.applicationContext)
    private val _uiState = MutableStateFlow(MyDailyUiState())
    val uiState: StateFlow<MyDailyUiState> = _uiState.asStateFlow()
    private var initialCheckDone = false

    init {
        refreshScheduleData()
    }

    fun refreshScheduleData(
        statusMessage: String? = null,
        forceNotificationCheck: Boolean = false
    ) {
        val app = getApplication<Application>()
        val context = app.applicationContext
        (app as? MyDailyApplication)?.registerCalendarObserverIfPermitted()
        ScheduleMonitorWorker.startRoutineMonitoring(context)

        val hasPerm = DeviceCalendarReader.hasCalendarPermission(context)
        val hasSource = repo.hasAnyConnectedSource()
        val selectedDate = _uiState.value.selectedDate
        val allItems = repo.getAllTimelineItems()
        val today = LocalDate.now()

        val weekDays = (-7L..30L).map { offset ->
            val date = today.plusDays(offset)
            CollisionDetector.buildDaySummary(date, allItems)
        }
        val currentDaySummary = CollisionDetector.buildDaySummary(selectedDate, allItems)

        // Find all other days (within -1..+30 days) that have collisions besides selectedDate
        val otherConflictDays = (-1L..30L)
            .map { today.plusDays(it) }
            .filter { it != selectedDate }
            .map { date -> CollisionDetector.buildDaySummary(date, allItems) }
            .filter { it.collisionPairs.isNotEmpty() }

        val shouldPromptConnect = if (!initialCheckDone) {
            initialCheckDone = true
            !hasPerm || !hasSource
        } else {
            _uiState.value.showConnectPromptDialog
        }

        _uiState.value = _uiState.value.copy(
            daySummary = currentDaySummary,
            allTimelineItems = allItems,
            weekSummaries = weekDays,
            otherDaysWithCollisions = otherConflictDays,
            connectedAccounts = repo.getConnectedAccountsSummary(),
            icsFeeds = repo.getIcsFeeds(),
            googleTasksToken = repo.getGoogleTasksToken(),
            googleAccountEmail = repo.getGoogleAccountEmail(),
            hasCalendarPermission = hasPerm,
            hasAnyConnectedSource = hasSource,
            showConnectPromptDialog = shouldPromptConnect,
            statusBannerMessage = statusMessage ?: _uiState.value.statusBannerMessage
        )

        CollisionNotificationManager.evaluateAndNotify(context, forceNotifyIfAny = forceNotificationCheck)
        MyDailyWidgetProvider.refreshAllWidgets(context)
    }

    fun openConnectPromptDialog() {
        _uiState.value = _uiState.value.copy(showConnectPromptDialog = true)
    }

    fun dismissConnectPromptDialog() {
        _uiState.value = _uiState.value.copy(showConnectPromptDialog = false)
    }

    fun selectDate(date: LocalDate) {
        val currentTab = _uiState.value.activeTab
        val targetTab = if (currentTab == AppTab.CONNECTIONS_WIDGET) AppTab.TIMELINE else currentTab
        _uiState.value = _uiState.value.copy(
            selectedDate = date,
            activeTab = targetTab,
            statusBannerMessage = null
        )
        refreshScheduleData()
    }

    fun selectFilter(filter: SourceFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
    }

    fun selectTab(tab: AppTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun dismissStatusBanner() {
        _uiState.value = _uiState.value.copy(statusBannerMessage = null)
    }

    fun onCalendarPermissionResult(granted: Boolean) {
        val context = getApplication<Application>().applicationContext
        val discovered = if (granted) {
            DeviceCalendarReader.getAvailableCalendars(context, repo.getDisabledCalendarIds())
        } else emptyList()

        val msg = when {
            granted && discovered.isNotEmpty() -> {
                _uiState.value = _uiState.value.copy(showConnectPromptDialog = false)
                "Connected ${discovered.size} calendar(s)!"
            }
            granted -> {
                "Calendar access granted!"
            }
            else -> {
                "Calendar permission denied. Please grant calendar access to sync your events."
            }
        }
        refreshScheduleData(msg, forceNotificationCheck = true)
    }

    fun onGoogleAccountAuthorized(email: String, oauthToken: String?) {
        repo.setGoogleAccountEmail(email)
        if (!oauthToken.isNullOrBlank()) {
            repo.setGoogleTasksToken(oauthToken)
            _uiState.value = _uiState.value.copy(showConnectPromptDialog = false)
            syncAllSources("Connected Google Tasks for $email!")
        } else {
            refreshScheduleData("Selected Google account $email.")
        }
    }

    fun onGoogleAccountAuthError(email: String, errorMsg: String) {
        repo.setGoogleAccountEmail(email)
        refreshScheduleData("Selected $email ($errorMsg).")
    }

    fun toggleTaskCompleted(item: UnifiedTimelineItem) {
        repo.toggleTaskCompleted(item.id)
        val nextState = !item.isCompleted
        val msg = if (nextState) {
            "Marked '${item.title}' as completed. Any collisions with it are now resolved!"
        } else {
            "Reopened Google Task '${item.title}'."
        }
        refreshScheduleData(msg)
    }

    fun autoResolveCollision(item: UnifiedTimelineItem) {
        val dayItems = _uiState.value.daySummary.items
        val (newStart, newEnd) = CollisionDetector.findNextFreeSlot(
            dayItems = dayItems,
            movingItemId = item.id,
            afterTimeMillis = item.startTimeMillis,
            durationMinutes = item.durationMinutes
        )
        repo.saveTimeOverride(item.id, newStart, newEnd)

        val zone = ZoneId.systemDefault()
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val startStr = Instant.ofEpochMilli(newStart).atZone(zone).format(fmt)
        val endStr = Instant.ofEpochMilli(newEnd).atZone(zone).format(fmt)

        refreshScheduleData("Resolved collision! Moved '${item.title}' to $startStr – $endStr.")
    }

    fun resetRescheduledTimes() {
        repo.clearTimeOverrides()
        refreshScheduleData("Restored original event times.", forceNotificationCheck = true)
    }

    fun addScheduleItem(
        title: String,
        subtitle: String,
        location: String,
        description: String,
        source: ItemSource,
        kind: ItemKind,
        startHour: Int,
        startMinute: Int,
        durationMinutes: Int
    ) {
        val zone = ZoneId.systemDefault()
        val date = _uiState.value.selectedDate
        val startMillis = date.atTime(startHour, startMinute).atZone(zone).toInstant().toEpochMilli()
        val endMillis = startMillis + durationMinutes.coerceAtLeast(15) * 60_000L

        val newItem = UnifiedTimelineItem(
            id = "custom_${UUID.randomUUID()}",
            title = title.trim().ifEmpty { "New ${kind.label}" },
            subtitle = subtitle.trim().ifEmpty { source.displayName },
            description = description.trim(),
            location = location.trim(),
            startTimeMillis = startMillis,
            endTimeMillis = endMillis,
            isAllDay = false,
            source = source,
            kind = kind,
            isCompleted = false,
            isEditable = true
        )
        repo.addCustomItem(newItem)
        refreshScheduleData(
            statusMessage = "Added '${newItem.title}' & updated Home Screen Widget.",
            forceNotificationCheck = true
        )
    }

    fun deleteCustomItem(itemId: String) {
        repo.deleteCustomItem(itemId)
        refreshScheduleData("Deleted item.")
    }

    fun toggleAccountEnabled(accountId: String) {
        repo.toggleCalendarAccount(accountId)
        refreshScheduleData(forceNotificationCheck = true)
    }

    fun addIcsFeed(label: String, url: String, source: ItemSource) {
        repo.addIcsFeed(label, url, source)
        _uiState.value = _uiState.value.copy(showConnectPromptDialog = false)
        syncAllSources()
    }

    fun removeIcsFeed(feedId: String) {
        repo.removeIcsFeed(feedId)
        refreshScheduleData("Removed ICS calendar feed.")
    }

    fun saveGoogleTasksToken(token: String) {
        repo.setGoogleTasksToken(token)
        if (token.isNotBlank()) {
            _uiState.value = _uiState.value.copy(showConnectPromptDialog = false)
            syncAllSources()
        } else {
            refreshScheduleData("Cleared Google Tasks OAuth token.")
        }
    }

    fun syncAllSources(customPrefix: String? = null) {
        _uiState.value = _uiState.value.copy(
            isSyncing = true,
            statusBannerMessage = customPrefix ?: "Checking calendars, tasks & collisions..."
        )
        viewModelScope.launch {
            val summaryMsg = withContext(Dispatchers.IO) {
                repo.syncRemoteSources()
            }
            _uiState.value = _uiState.value.copy(isSyncing = false)
            val finalMsg = if (customPrefix != null) "$customPrefix • $summaryMsg" else "Synced • $summaryMsg"
            refreshScheduleData(finalMsg, forceNotificationCheck = true)
        }
    }
}
