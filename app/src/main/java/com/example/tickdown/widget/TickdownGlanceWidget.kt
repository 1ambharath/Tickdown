package com.example.tickdown.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.datastore.preferences.core.Preferences
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.tickdown.MainActivity
import com.example.tickdown.R
import com.example.tickdown.data.local.AppDatabase
import com.example.tickdown.data.local.entity.toDomain
import com.example.tickdown.data.preferences.WidgetConfig
import com.example.tickdown.data.preferences.WidgetConfigRepository
import com.example.tickdown.data.preferences.WidgetDisplayMode
import com.example.tickdown.domain.model.CountdownEvent
import com.example.tickdown.domain.model.EventStatus
import com.example.tickdown.domain.model.RecurrenceRule
import com.example.tickdown.domain.recurrence.OccurrenceDetails
import com.example.tickdown.domain.recurrence.RecurrenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// Adaptive Monochrome Color Providers for Light & Dark Widget Themes via XML resources
private val WidgetBackground = ColorProvider(R.color.widget_background)
private val WidgetSurface = ColorProvider(R.color.widget_surface)
private val WidgetTextPrimary = ColorProvider(R.color.widget_text_primary)
private val WidgetTextMuted = ColorProvider(R.color.widget_text_muted)
private val WidgetOutline = ColorProvider(R.color.widget_outline)
private val WidgetAccent = ColorProvider(R.color.widget_accent)
private val WidgetOverdue = ColorProvider(R.color.widget_overdue)

class TickdownGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable
    ) {
        android.util.Log.e("TickdownGlanceWidget", "Composition error in widget $appWidgetId", throwable)
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val rv = RemoteViews(context.packageName, R.layout.widget_error_layout).apply {
                val refreshIntent = Intent(context, TickdownGlanceWidgetReceiver::class.java).apply {
                    action = TickdownGlanceWidgetReceiver.ACTION_AUTO_REFRESH_WIDGET
                }
                val pi = android.app.PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    refreshIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_error_root, pi)
            }
            appWidgetManager.updateAppWidget(appWidgetId, rv)
        } catch (_: Exception) {
            super.onCompositionError(context, glanceId, appWidgetId, throwable)
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = try {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (_: Exception) {
            AppWidgetManager.INVALID_APPWIDGET_ID
        }

        val configRepo = WidgetConfigRepository(context)
        val initialConfig = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            try {
                configRepo.getWidgetConfig(appWidgetId)
            } catch (_: Exception) {
                WidgetConfig(appWidgetId = appWidgetId)
            }
        } else {
            WidgetConfig(appWidgetId = appWidgetId)
        }

        // Safely preload database snapshot on Dispatchers.IO
        val db = AppDatabase.getDatabase(context)
        val initialEntities = withContext(Dispatchers.IO) {
            try {
                db.countdownDao().getAllEventsFlow().first()
            } catch (_: Exception) {
                emptyList()
            }
        }

        provideContent {
            // Read widget preferences to bind composition to Glance state updates
            val prefs = currentState<Preferences>()
            val lastUpdateEpoch = prefs[TickdownWidgetUpdater.LAST_UPDATED_KEY]

            val currentContext = LocalContext.current
            val currentConfigRepo = remember { WidgetConfigRepository(currentContext) }
            val config = remember(appWidgetId, lastUpdateEpoch) {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    try {
                        currentConfigRepo.getWidgetConfig(appWidgetId)
                    } catch (_: Exception) {
                        initialConfig
                    }
                } else {
                    initialConfig
                }
            }

            val allEvents = remember(initialEntities) {
                initialEntities.map { it.toDomain() }
            }

            val selectedEvents = remember(allEvents, config) {
                if (config.displayMode == WidgetDisplayMode.PINNED && config.pinnedEventId != null) {
                    val pinned = allEvents.find { it.id == config.pinnedEventId && it.status == EventStatus.ACTIVE }
                    if (pinned != null) listOf(pinned) else allEvents.filter { it.status == EventStatus.ACTIVE }
                } else {
                    allEvents.filter { it.status == EventStatus.ACTIVE }
                }
            }

            val now = ZonedDateTime.now()
            // Safely calculate details whenever selected events, update timestamp, or time progresses
            val eventsWithDetails = remember(selectedEvents, lastUpdateEpoch) {
                selectedEvents.mapNotNull { event ->
                    try {
                        val details = RecurrenceEngine.calculateOccurrenceDetails(event, now)
                        Pair(event, details)
                    } catch (_: Exception) {
                        null
                    }
                }.sortedWith(
                    compareBy(
                        { it.first.status == EventStatus.COMPLETED },
                        { it.first.status == EventStatus.PAUSED },
                        { it.second.occurrenceDateTime.toInstant() }
                    )
                )
            }

            val size = LocalSize.current
            val activeCount = eventsWithDetails.count { it.first.status == EventStatus.ACTIVE }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(WidgetBackground)
                    .cornerRadius(20.dp)
                    .padding(12.dp)
            ) {
                if (eventsWithDetails.isEmpty()) {
                    WidgetEmptyState()
                } else {
                    when {
                        // Compact layout: small square or short rectangle (1 priority event)
                        size.height < 130.dp || size.width < 160.dp -> {
                            CompactWidgetLayout(item = eventsWithDetails.first())
                        }

                        // Standard layout: medium rectangle (nearest 3-4 events)
                        size.height <= 220.dp -> {
                            StandardWidgetLayout(
                                items = eventsWithDetails.take(4),
                                totalActive = activeCount
                            )
                        }

                        // Expanded layout: large full widget (5+ events with secondary date specs)
                        else -> {
                            ExpandedWidgetLayout(
                                items = eventsWithDetails.take(6),
                                totalActive = activeCount
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Layouts ---

@androidx.compose.runtime.Composable
private fun LiveHeroCountdown(
    details: OccurrenceDetails,
    isTimed: Boolean,
    layoutRes: Int,
    chronometerId: Int,
    textSizeSp: Float
) {
    val context = LocalContext.current
    val targetMillis = details.occurrenceDateTime.toInstant().toEpochMilli()
    val nowMillis = System.currentTimeMillis()
    val totalRemainingMillis = targetMillis - nowMillis

    if (details.isOverdue || totalRemainingMillis <= 0) {
        Text(
            text = formatHeroCountdown(details),
            style = TextStyle(
                color = WidgetOverdue,
                fontSize = textSizeSp.sp,
                fontWeight = FontWeight.Bold
            )
        )
    } else if (!isTimed) {
        Text(
            text = formatHeroCountdown(details),
            style = TextStyle(
                color = WidgetTextPrimary,
                fontSize = textSizeSp.sp,
                fontWeight = FontWeight.Bold
            )
        )
    } else {
        val days = details.daysRemaining
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            if (days > 0) {
                Text(
                    text = "${days}d ",
                    style = TextStyle(
                        color = WidgetTextPrimary,
                        fontSize = textSizeSp.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            val remainingForChronometer = if (days > 0) {
                val h = details.hoursRemaining
                val m = details.minutesRemaining
                val s = details.secondsRemaining
                (h * 3600 + m * 60 + s) * 1000L
            } else {
                totalRemainingMillis
            }
            val baseElapsedRealtime = SystemClock.elapsedRealtime() + remainingForChronometer
            val rv = RemoteViews(context.packageName, layoutRes).apply {
                setChronometerCountDown(chronometerId, true)
                setChronometer(chronometerId, baseElapsedRealtime, "%s", true)
            }
            AndroidRemoteViews(remoteViews = rv)
        }
    }
}

@androidx.compose.runtime.Composable
private fun CompactWidgetLayout(item: Pair<CountdownEvent, OccurrenceDetails>) {
    val (event, details) = item
    val context = LocalContext.current

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionOpenEvent(context, event.id)),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        // Mini Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "TICKDOWN",
                style = TextStyle(
                    color = WidgetTextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = "↺",
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshWidgetActionCallback>()),
                style = TextStyle(
                    color = WidgetTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = formatCompactRecurrence(event.recurrenceRule),
                style = TextStyle(
                    color = WidgetTextMuted,
                    fontSize = 9.sp
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        // Title
        Text(
            text = event.title,
            maxLines = 1,
            style = TextStyle(
                color = WidgetTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        // Main Countdown (Live Ticking)
        LiveHeroCountdown(
            details = details,
            isTimed = event.isTimed,
            layoutRes = R.layout.widget_live_chronometer_hero,
            chronometerId = R.id.live_chronometer_hero,
            textSizeSp = 20f
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        // Thin Progress Indicator
        val progress = calculateProgressRatio(event, details)
        if (progress != null) {
            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth().height(3.dp),
                color = WidgetAccent,
                backgroundColor = WidgetOutline
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
        }

        // Secondary Date
        Text(
            text = formatCompactDate(details, event.isTimed),
            style = TextStyle(
                color = WidgetTextMuted,
                fontSize = 10.sp
            )
        )
    }
}

@androidx.compose.runtime.Composable
private fun StandardWidgetLayout(
    items: List<Pair<CountdownEvent, OccurrenceDetails>>,
    totalActive: Int
) {
    val context = LocalContext.current

    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "TICKDOWN",
                style = TextStyle(
                    color = WidgetTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "$totalActive ACTIVE",
                style = TextStyle(
                    color = WidgetTextMuted,
                    fontSize = 10.sp
                )
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "↺",
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshWidgetActionCallback>()),
                style = TextStyle(
                    color = WidgetTextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())

            // Add action button
            Text(
                text = "+ ADD",
                modifier = GlanceModifier
                    .background(WidgetSurface)
                    .cornerRadius(10.dp)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clickable(actionCreateEvent(context)),
                style = TextStyle(
                    color = WidgetTextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        // Events list
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            items.forEachIndexed { index, (event, details) ->
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable(actionOpenEvent(context, event.id)),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = event.title,
                            maxLines = 1,
                            style = TextStyle(
                                color = WidgetTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(8.dp))

                    if (event.isTimed && !details.isOverdue) {
                        LiveHeroCountdown(
                            details = details,
                            isTimed = true,
                            layoutRes = R.layout.widget_live_chronometer_standard,
                            chronometerId = R.id.live_chronometer_standard,
                            textSizeSp = 12f
                        )
                    } else {
                        Text(
                            text = formatCompactCountdownValue(details),
                            style = TextStyle(
                                color = if (details.isOverdue) WidgetOverdue else WidgetTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                if (index < items.size - 1) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(WidgetOutline)
                    ) {}
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ExpandedWidgetLayout(
    items: List<Pair<CountdownEvent, OccurrenceDetails>>,
    totalActive: Int
) {
    val context = LocalContext.current

    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "TICKDOWN",
                style = TextStyle(
                    color = WidgetTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = "$totalActive ACTIVE COUNTDOWNS",
                style = TextStyle(
                    color = WidgetTextMuted,
                    fontSize = 10.sp
                )
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "↺",
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshWidgetActionCallback>()),
                style = TextStyle(
                    color = WidgetTextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())

            // Add action button
            Text(
                text = "+ NEW EVENT",
                modifier = GlanceModifier
                    .background(WidgetSurface)
                    .cornerRadius(12.dp)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clickable(actionCreateEvent(context)),
                style = TextStyle(
                    color = WidgetTextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Expanded items
        Column(modifier = GlanceModifier.fillMaxSize()) {
            items.forEachIndexed { index, (event, details) ->
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable(actionOpenEvent(context, event.id)),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                            Text(
                                text = event.title,
                                maxLines = 1,
                                style = TextStyle(
                                    color = WidgetTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            Text(
                                text = formatCompactRecurrence(event.recurrenceRule),
                                style = TextStyle(
                                    color = WidgetTextMuted,
                                    fontSize = 9.sp
                                )
                            )
                        }

                        Text(
                            text = formatSecondaryDate(details, event.isTimed),
                            style = TextStyle(
                                color = WidgetTextMuted,
                                fontSize = 10.sp
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(8.dp))

                    LiveHeroCountdown(
                        details = details,
                        isTimed = event.isTimed,
                        layoutRes = R.layout.widget_live_chronometer_expanded,
                        chronometerId = R.id.live_chronometer_expanded,
                        textSizeSp = 14f
                    )
                }

                val progress = calculateProgressRatio(event, details)
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = GlanceModifier.fillMaxWidth().height(2.dp),
                        color = WidgetAccent,
                        backgroundColor = WidgetOutline
                    )
                }

                if (index < items.size - 1) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetEmptyState() {
    val context = LocalContext.current

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Text(
            text = "NO COUNTDOWNS",
            style = TextStyle(
                color = WidgetTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "Tap to track your first event",
            style = TextStyle(
                color = WidgetTextMuted,
                fontSize = 11.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        Text(
            text = "+ CREATE EVENT",
            modifier = GlanceModifier
                .background(WidgetSurface)
                .cornerRadius(12.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable(actionCreateEvent(context)),
            style = TextStyle(
                color = WidgetTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

// --- Helpers & Intent Actions ---

private fun actionOpenEvent(context: Context, eventId: Long): androidx.glance.action.Action {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = MainActivity.ACTION_VIEW_EVENT
        putExtra(MainActivity.EXTRA_EVENT_ID, eventId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return actionStartActivity(intent)
}

private fun actionCreateEvent(context: Context): androidx.glance.action.Action {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = MainActivity.ACTION_CREATE_EVENT
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return actionStartActivity(intent)
}

class RefreshWidgetActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        TickdownWidgetUpdater.updateAllWidgets(context)
    }
}

private fun formatHeroCountdown(details: OccurrenceDetails): String {
    val d = details.daysRemaining
    val h = details.hoursRemaining
    val m = details.minutesRemaining
    val s = details.secondsRemaining

    if (details.isOverdue) {
        return when {
            d > 0 -> "${d}d ${h}h OVERDUE"
            h > 0 -> "${h}h ${m}m OVERDUE"
            else -> "${m}m OVERDUE"
        }
    }

    return when {
        // Show days, hours, and minutes so the user sees the countdown progressing
        d > 0 -> "${d}d ${h.toString().padStart(2, '0')}h ${m.toString().padStart(2, '0')}m"
        h > 0 -> "${h}h ${m.toString().padStart(2, '0')}m ${s.toString().padStart(2, '0')}s"
        m > 0 -> "${m}m ${s.toString().padStart(2, '0')}s"
        else -> "${s}s"
    }
}

private fun formatCompactCountdownValue(details: OccurrenceDetails): String {
    val d = details.daysRemaining
    val h = details.hoursRemaining
    val m = details.minutesRemaining

    return when {
        details.isToday -> "Today"
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

private fun formatCompactDate(details: OccurrenceDetails, isTimed: Boolean): String {
    val date = details.occurrenceDateTime.format(DateTimeFormatter.ofPattern("MMM dd"))
    return if (isTimed) {
        val time = details.occurrenceDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        "$date • $time"
    } else {
        date
    }
}

private fun formatSecondaryDate(details: OccurrenceDetails, isTimed: Boolean): String {
    val date = details.occurrenceDateTime.format(DateTimeFormatter.ofPattern("EEE, MMM dd"))
    return if (isTimed) {
        val time = details.occurrenceDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        "$date at $time"
    } else {
        "$date (All day)"
    }
}

private fun formatCompactRecurrence(rule: RecurrenceRule): String {
    return when (rule) {
        is RecurrenceRule.OneTime -> "1-TIME"
        is RecurrenceRule.Daily -> "DAILY"
        is RecurrenceRule.Weekly -> "WEEKLY"
        is RecurrenceRule.Monthly.DayOfMonth -> "M-${rule.day}"
        is RecurrenceRule.Monthly.LastDay -> "M-LAST"
        is RecurrenceRule.Yearly -> "YEARLY"
    }
}

/**
 * Calculates a progress ratio (0.0 to 1.0) when meaningful (e.g. from creation/completion to target).
 */
private fun calculateProgressRatio(event: CountdownEvent, details: OccurrenceDetails): Float? {
    if (details.isOverdue) return 1.0f
    val startInstant = event.lastCompletedAt ?: event.createdAt
    val targetInstant = details.occurrenceDateTime.toInstant()
    val totalSeconds = Duration.between(startInstant, targetInstant).seconds
    if (totalSeconds <= 0) return null

    val elapsedSeconds = Duration.between(startInstant, java.time.Instant.now()).seconds
    return (elapsedSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
}
