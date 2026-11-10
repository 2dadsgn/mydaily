package com.mydaily.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mydaily.app.domain.CollisionDetector
import com.mydaily.app.model.CollisionPair
import com.mydaily.app.model.DayScheduleSummary
import com.mydaily.app.model.ItemKind
import com.mydaily.app.model.ItemSource
import com.mydaily.app.model.UnifiedTimelineItem
import com.mydaily.app.ui.theme.ClearEmerald
import com.mydaily.app.ui.theme.CollisionAmber
import com.mydaily.app.ui.theme.CollisionCrimson
import com.mydaily.app.ui.theme.ConflictBannerBackground
import com.mydaily.app.ui.theme.ConflictCardBackground
import com.mydaily.app.ui.theme.ConflictTextPrimary
import com.mydaily.app.ui.theme.FutureConflictBannerBackground
import com.mydaily.app.ui.theme.FutureConflictTextPrimary
import com.mydaily.app.ui.theme.GoogleCalendarBlue
import com.mydaily.app.ui.theme.GoogleTasksGreen
import com.mydaily.app.ui.theme.InfoBannerBackground
import com.mydaily.app.ui.theme.InfoBannerText
import com.mydaily.app.ui.theme.MyDailyTheme
import com.mydaily.app.ui.theme.SlateBackground
import com.mydaily.app.ui.theme.SlateSurface
import com.mydaily.app.ui.theme.SlateSurfaceVariant
import com.mydaily.app.ui.theme.TextPrimary
import com.mydaily.app.ui.theme.TextSecondary
import com.mydaily.app.ui.theme.TodayHighlightRing
import com.mydaily.app.ui.theme.TodayUnselectedBackground
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val PAGER_CENTER_PAGE = 500
private const val PAGER_TOTAL_PAGES = 1000

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MyDailyAppScreen(
    uiState: MyDailyUiState,
    onSelectDate: (LocalDate) -> Unit,
    onSelectFilter: (SourceFilter) -> Unit,
    onSelectTab: (AppTab) -> Unit,
    onToggleTaskCompleted: (UnifiedTimelineItem) -> Unit,
    onAutoResolveCollision: (UnifiedTimelineItem) -> Unit,
    onResetRescheduledTimes: () -> Unit,
    onAddScheduleItem: (String, String, String, String, ItemSource, ItemKind, Int, Int, Int) -> Unit,
    onDeleteCustomItem: (String) -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onConnectGoogleTasksAccount: () -> Unit,
    onOpenConnectDialog: () -> Unit,
    onDismissConnectDialog: () -> Unit,
    onToggleAccount: (String) -> Unit,
    onAddIcsFeed: (String, String, ItemSource) -> Unit,
    onRemoveIcsFeed: (String) -> Unit,
    onSaveGoogleTasksToken: (String) -> Unit,
    onSyncNow: () -> Unit,
    onDismissBanner: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val baseDate = remember { LocalDate.now() }

    fun pageForDate(date: LocalDate): Int =
        (PAGER_CENTER_PAGE + ChronoUnit.DAYS.between(baseDate, date).toInt())
            .coerceIn(0, PAGER_TOTAL_PAGES - 1)

    fun dateForPage(page: Int): LocalDate =
        baseDate.plusDays((page - PAGER_CENTER_PAGE).toLong())

    val pagerState = rememberPagerState(
        initialPage = pageForDate(uiState.selectedDate),
        pageCount = { PAGER_TOTAL_PAGES }
    )

    // Keep ViewModel selectedDate in sync when user finishes sliding to a page
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val settledDate = dateForPage(page)
            if (settledDate != uiState.selectedDate) {
                onSelectDate(settledDate)
            }
        }
    }

    // Smoothly slide pager when selectedDate is changed via top strip, "Today", or notification
    LaunchedEffect(uiState.selectedDate) {
        val targetPage = pageForDate(uiState.selectedDate)
        if (pagerState.settledPage != targetPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    val activeDisplayDate = dateForPage(pagerState.currentPage)

    Scaffold(
        containerColor = SlateBackground,
        topBar = {
            SimpleTopHeader(
                uiState = uiState,
                displayDate = activeDisplayDate,
                onSelectDate = onSelectDate,
                onSyncNow = onSyncNow,
                onDismissBanner = onDismissBanner
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SlateSurface,
                modifier = Modifier.navigationBarsPadding()
            ) {
                NavigationBarItem(
                    selected = uiState.activeTab == AppTab.TIMELINE,
                    onClick = { onSelectTab(AppTab.TIMELINE) },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                    label = { Text("Schedule", fontSize = 12.sp) }
                )
                NavigationBarItem(
                    selected = uiState.activeTab == AppTab.OVERLAP_GRID,
                    onClick = { onSelectTab(AppTab.OVERLAP_GRID) },
                    icon = { Icon(Icons.Default.ViewTimeline, contentDescription = null) },
                    label = { Text("Day Grid", fontSize = 12.sp) }
                )
                NavigationBarItem(
                    selected = uiState.activeTab == AppTab.CONNECTIONS_WIDGET,
                    onClick = { onSelectTab(AppTab.CONNECTIONS_WIDGET) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Calendars & Widget", fontSize = 12.sp) }
                )
            }
        },
        floatingActionButton = {
            if (uiState.activeTab != AppTab.CONNECTIONS_WIDGET) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = GoogleCalendarBlue,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Event or Task")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (uiState.activeTab) {
                AppTab.TIMELINE -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Stationary Filter Chips Bar above the fluid day pager
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            SourceFilter.entries.forEach { filter ->
                                val selected = uiState.selectedFilter == filter
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectFilter(filter) },
                                    label = { Text(filter.label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (filter == SourceFilter.COLLISIONS_ONLY) {
                                            CollisionCrimson
                                        } else {
                                            GoogleCalendarBlue
                                        },
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        // Fluid finger-tracking HorizontalPager for Schedule Days
                        HorizontalPager(
                            state = pagerState,
                            beyondBoundsPageCount = 1,
                            pageSpacing = 12.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) { page ->
                            val pageDate = dateForPage(page)
                            val pageSummary = remember(pageDate, uiState.allTimelineItems) {
                                CollisionDetector.buildDaySummary(pageDate, uiState.allTimelineItems)
                            }
                            CleanScheduleTab(
                                uiState = uiState,
                                daySummary = pageSummary,
                                onSelectDate = onSelectDate,
                                onToggleTaskCompleted = onToggleTaskCompleted,
                                onAutoResolveCollision = onAutoResolveCollision,
                                onOpenAccountsTab = { onSelectTab(AppTab.CONNECTIONS_WIDGET) },
                                onDeleteCustomItem = onDeleteCustomItem
                            )
                        }
                    }
                }

                AppTab.OVERLAP_GRID -> {
                    // Fluid finger-tracking HorizontalPager for Day Grid
                    HorizontalPager(
                        state = pagerState,
                        beyondBoundsPageCount = 1,
                        pageSpacing = 12.dp,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val pageDate = dateForPage(page)
                        val pageSummary = remember(pageDate, uiState.allTimelineItems) {
                            CollisionDetector.buildDaySummary(pageDate, uiState.allTimelineItems)
                        }
                        OverlapDayGridView(
                            daySummary = pageSummary,
                            onAutoResolve = onAutoResolveCollision,
                            onToggleTaskDone = onToggleTaskCompleted
                        )
                    }
                }

                AppTab.CONNECTIONS_WIDGET -> ConnectionsAndWidgetTab(
                    uiState = uiState,
                    onRequestCalendarPermission = onRequestCalendarPermission,
                    onConnectGoogleTasksAccount = onConnectGoogleTasksAccount,
                    onToggleAccount = onToggleAccount,
                    onAddIcsFeed = onAddIcsFeed,
                    onRemoveIcsFeed = onRemoveIcsFeed,
                    onSaveGoogleTasksToken = onSaveGoogleTasksToken,
                    onResetRescheduledTimes = onResetRescheduledTimes,
                    onSyncNow = onSyncNow
                )
            }
        }

        if (uiState.showConnectPromptDialog) {
            ConnectCalendarsDialog(
                uiState = uiState,
                onRequestCalendarPermission = onRequestCalendarPermission,
                onConnectGoogleTasksAccount = onConnectGoogleTasksAccount,
                onAddIcsFeed = onAddIcsFeed,
                onDismiss = onDismissConnectDialog
            )
        }

        if (showAddDialog) {
            AddScheduleItemDialog(
                selectedDate = uiState.selectedDate,
                existingDayItems = uiState.daySummary.items,
                onDismiss = { showAddDialog = false },
                onConfirm = { title, subtitle, loc, desc, source, kind, hr, min, dur ->
                    onAddScheduleItem(title, subtitle, loc, desc, source, kind, hr, min, dur)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
private fun SimpleTopHeader(
    uiState: MyDailyUiState,
    displayDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onSyncNow: () -> Unit,
    onDismissBanner: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateSurface)
            // Respect Android status bar height (battery, reception, camera cutout)
            .statusBarsPadding()
            .padding(top = 8.dp, bottom = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
        ) {
            Column {
                Text(
                    text = displayDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Google • Outlook • Tasks",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (displayDate != LocalDate.now()) {
                    TextButton(onClick = { onSelectDate(LocalDate.now()) }) {
                        Text("Today", color = GoogleCalendarBlue, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(onClick = onSyncNow) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = TextPrimary
                    )
                }
            }
        }

        AnimatedVisibility(visible = !uiState.statusBannerMessage.isNullOrBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(InfoBannerBackground)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = uiState.statusBannerMessage.orEmpty(),
                    color = InfoBannerText,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismissBanner,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = InfoBannerText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Clean 7-Day Strip (auto-scrolls as user swipes left/right between days)
        val stripListState = rememberLazyListState()
        LaunchedEffect(displayDate, uiState.weekSummaries.size) {
            val idx = uiState.weekSummaries.indexOfFirst { it.date == displayDate }
            if (idx >= 0) {
                stripListState.animateScrollToItem((idx - 2).coerceAtLeast(0))
            }
        }

        LazyRow(
            state = stripListState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.weekSummaries, key = { it.date.toEpochDay() }) { daySum ->
                val isSelected = daySum.date == displayDate
                val isToday = daySum.date == LocalDate.now()
                val hasConflict = daySum.collisionPairs.isNotEmpty()

                val pillBg = when {
                    isSelected -> GoogleCalendarBlue
                    isToday -> TodayUnselectedBackground
                    else -> SlateSurfaceVariant.copy(alpha = 0.6f)
                }
                val borderWidth = if (isToday) 2.dp else 0.dp
                val borderColor = when {
                    isToday && isSelected -> Color(0xFF93C5FD)
                    isToday -> TodayHighlightRing
                    else -> Color.Transparent
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(pillBg)
                        .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
                        .clickable { onSelectDate(daySum.date) }
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = if (isToday) "TODAY" else daySum.date.format(DateTimeFormatter.ofPattern("EEE")),
                        color = when {
                            isSelected -> Color.White
                            isToday -> TodayHighlightRing
                            else -> TextSecondary
                        },
                        fontSize = if (isToday) 9.5.sp else 11.sp,
                        fontWeight = if (isToday || isSelected) FontWeight.ExtraBold else FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = daySum.date.dayOfMonth.toString(),
                        color = if (isSelected) Color.White else TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    hasConflict -> CollisionCrimson
                                    daySum.items.isNotEmpty() -> ClearEmerald
                                    isToday -> if (isSelected) Color.White else TodayHighlightRing
                                    else -> Color.Transparent
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun CleanScheduleTab(
    uiState: MyDailyUiState,
    daySummary: DayScheduleSummary,
    onSelectDate: (LocalDate) -> Unit,
    onToggleTaskCompleted: (UnifiedTimelineItem) -> Unit,
    onAutoResolveCollision: (UnifiedTimelineItem) -> Unit,
    onOpenAccountsTab: () -> Unit,
    onDeleteCustomItem: (String) -> Unit
) {
    val filteredItems = remember(daySummary.items, uiState.selectedFilter) {
        when (uiState.selectedFilter) {
            SourceFilter.ALL -> daySummary.items
            SourceFilter.COLLISIONS_ONLY -> daySummary.items.filter { it.hasCollision }
            SourceFilter.GOOGLE_CALENDAR -> daySummary.items.filter { it.source == ItemSource.GOOGLE_CALENDAR }
            SourceFilter.OUTLOOK_CALENDAR -> daySummary.items.filter { it.source == ItemSource.OUTLOOK_CALENDAR }
            SourceFilter.GOOGLE_TASKS -> daySummary.items.filter { it.source == ItemSource.GOOGLE_TASKS }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Selected Day Conflict Alert Card (shown when a collision exists on this page's day)
        if (daySummary.collisionPairs.isNotEmpty()) {
            item {
                SimpleConflictBanner(pairs = daySummary.collisionPairs)
            }
        }

        // Collisions on Other Days Banner (shown when another upcoming day has collisions)
        if (uiState.otherDaysWithCollisions.isNotEmpty()) {
            item {
                OtherDaysConflictBanner(
                    otherConflictDays = uiState.otherDaysWithCollisions,
                    onJumpToDate = onSelectDate
                )
            }
        }

        // Empty State or Clean Event Rows
        if (filteredItems.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateSurface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp)
                    ) {
                        Text(
                            text = if (!uiState.hasAnyConnectedSource) {
                                "Connect your calendars to get started"
                            } else {
                                "Nothing scheduled for this day"
                            },
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (!uiState.hasAnyConnectedSource) {
                                "Link Google Calendar, Outlook, and Google Tasks in the Calendars tab."
                            } else {
                                "Tap '+' to add an event or task, or select another day above."
                            },
                            color = TextSecondary,
                            fontSize = 12.5.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onOpenAccountsTab,
                            colors = ButtonDefaults.buttonColors(containerColor = GoogleCalendarBlue)
                        ) {
                            Text("Manage Calendars & Outlook Sync", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(filteredItems, key = { it.id }) { item ->
                CleanAgendaRow(
                    item = item,
                    onToggleTaskCompleted = { onToggleTaskCompleted(item) },
                    onAutoResolveCollision = { onAutoResolveCollision(item) },
                    onDeleteCustom = if (item.id.startsWith("custom_")) {
                        { onDeleteCustomItem(item.id) }
                    } else null
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SimpleConflictBanner(
    pairs: List<CollisionPair>
) {
    val firstPair = pairs.first()
    val extra = if (pairs.size > 1) " (+${pairs.size - 1} more)" else ""

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ConflictBannerBackground)
            .border(1.dp, CollisionCrimson, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.WarningAmber,
            contentDescription = null,
            tint = CollisionCrimson,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Conflict: ${firstPair.first.title} & ${firstPair.second.title}$extra",
            color = ConflictTextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OtherDaysConflictBanner(
    otherConflictDays: List<DayScheduleSummary>,
    onJumpToDate: (LocalDate) -> Unit
) {
    val nextDay = otherConflictDays.first()
    val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
    val extraDays = if (otherConflictDays.size > 1) " (+${otherConflictDays.size - 1} more)" else ""

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FutureConflictBannerBackground)
            .border(1.dp, CollisionAmber, RoundedCornerShape(12.dp))
            .clickable { onJumpToDate(nextDay.date) }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.WarningAmber,
            contentDescription = null,
            tint = CollisionAmber,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Conflict on ${nextDay.date.format(dateFmt)}$extraDays",
            color = FutureConflictTextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "View →",
            color = CollisionAmber,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun CleanAgendaRow(
    item: UnifiedTimelineItem,
    onToggleTaskCompleted: () -> Unit,
    onAutoResolveCollision: () -> Unit,
    onDeleteCustom: (() -> Unit)?
) {
    val zone = ZoneId.systemDefault()
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val startStr = if (item.isAllDay) "All" else Instant.ofEpochMilli(item.startTimeMillis).atZone(zone).format(timeFormatter)
    val endStr = if (item.isAllDay) "Day" else Instant.ofEpochMilli(item.endTimeMillis).atZone(zone).format(timeFormatter)
    val sourceColor = Color(item.source.colorHex)

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Left Time Column
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .width(48.dp)
                .padding(top = 10.dp, end = 10.dp)
        ) {
            Text(
                text = startStr,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = endStr,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        // Right Event/Task Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (item.hasCollision) ConflictCardBackground else SlateSurface
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1f)
                .border(
                    width = if (item.hasCollision) 1.5.dp else 0.dp,
                    color = if (item.hasCollision) CollisionCrimson else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Colored vertical indicator bar
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(74.dp)
                        .background(sourceColor)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.source.displayName,
                            color = sourceColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (item.kind == ItemKind.GOOGLE_TASK) {
                                IconButton(
                                    onClick = onToggleTaskCompleted,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (item.isCompleted) {
                                            Icons.Default.CheckCircle
                                        } else {
                                            Icons.Default.RadioButtonUnchecked
                                        },
                                        contentDescription = "Complete Task",
                                        tint = if (item.isCompleted) ClearEmerald else GoogleTasksGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            if (onDeleteCustom != null) {
                                IconButton(
                                    onClick = onDeleteCustom,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = item.title,
                        color = if (item.isCompleted) TextSecondary else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    )

                    if (item.location.isNotBlank() || item.subtitle.isNotBlank()) {
                        Text(
                            text = item.location.ifBlank { item.subtitle },
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (item.hasCollision) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color(0x28EF4444),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            val otherTitles = item.collisions.joinToString(", ") { it.otherTitle }
                            Text(
                                text = "⚠ Overlaps with $otherTitles",
                                color = ConflictTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
fun MyDailyScreenPreview() {
    val today = LocalDate.now()
    val summary = CollisionDetector.buildDaySummary(today, emptyList())
    val week = (-1L..5L).map { offset ->
        CollisionDetector.buildDaySummary(today.plusDays(offset), emptyList())
    }
    MyDailyTheme {
        MyDailyAppScreen(
            uiState = MyDailyUiState(
                selectedDate = today,
                daySummary = summary,
                weekSummaries = week
            ),
            onSelectDate = {},
            onSelectFilter = {},
            onSelectTab = {},
            onToggleTaskCompleted = {},
            onAutoResolveCollision = {},
            onResetRescheduledTimes = {},
            onAddScheduleItem = { _, _, _, _, _, _, _, _, _ -> },
            onDeleteCustomItem = {},
            onRequestCalendarPermission = {},
            onConnectGoogleTasksAccount = {},
            onOpenConnectDialog = {},
            onDismissConnectDialog = {},
            onToggleAccount = {},
            onAddIcsFeed = { _, _, _ -> },
            onRemoveIcsFeed = {},
            onSaveGoogleTasksToken = {},
            onSyncNow = {},
            onDismissBanner = {}
        )
    }
}
