package com.example.tickdown.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tickdown.domain.model.CountdownEvent
import com.example.tickdown.domain.model.EventStatus
import com.example.tickdown.domain.model.RecurrenceRule
import com.example.tickdown.domain.recurrence.OccurrenceDetails
import com.example.tickdown.theme.TickdownOverdueRed
import com.example.tickdown.ui.components.TickdownBadge
import com.example.tickdown.ui.components.TickdownButton
import com.example.tickdown.ui.components.TickdownCard
import com.example.tickdown.ui.components.TickdownStatusBadge
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddClick: () -> Unit,
    onEventClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                TickdownButton(
                    text = "+ NEW COUNTDOWN",
                    onClick = onAddClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Minimal Header Bar
            HomeHeader(
                activeCount = uiState.activeCount,
                totalCount = uiState.totalCount,
                onSettingsClick = onSettingsClick
            )

            // Filter Pills Row
            FilterRow(
                currentFilter = uiState.filterStatus,
                onFilterSelected = { viewModel.setFilter(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.secondary,
                            strokeWidth = 2.dp
                        )
                    }
                }

                uiState.events.isEmpty() -> {
                    EmptyStateCard(onAddClick = onAddClick)
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(uiState.events, key = { it.event.id }) { item ->
                            EventCard(
                                item = item,
                                onClick = { onEventClick(item.event.id) },
                                onComplete = { viewModel.completeEvent(item.event.id) },
                                onTogglePause = { viewModel.togglePauseEvent(item.event) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    activeCount: Int,
    totalCount: Int,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Tick",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "down",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(10.dp))
                TickdownBadge(
                    text = "$activeCount / $totalCount ACTIVE",
                    backgroundColor = MaterialTheme.colorScheme.secondary,
                    textColor = MaterialTheme.colorScheme.onSecondary
                )
            }
            Text(
                text = "MINIMAL MULTI-COUNTDOWN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Settings Pill Button
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline), CircleShape)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .clickable(onClick = onSettingsClick)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun FilterRow(
    currentFilter: EventStatus?,
    onFilterSelected: (EventStatus?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                label = "ALL",
                selected = currentFilter == null,
                onClick = { onFilterSelected(null) }
            )
        }
        item {
            FilterChip(
                label = "ACTIVE",
                selected = currentFilter == EventStatus.ACTIVE,
                onClick = { onFilterSelected(EventStatus.ACTIVE) }
            )
        }
        item {
            FilterChip(
                label = "PAUSED",
                selected = currentFilter == EventStatus.PAUSED,
                onClick = { onFilterSelected(EventStatus.PAUSED) }
            )
        }
        item {
            FilterChip(
                label = "COMPLETED",
                selected = currentFilter == EventStatus.COMPLETED,
                onClick = { onFilterSelected(EventStatus.COMPLETED) }
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(100.dp)
    val bg = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface
    val text = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
    val border = if (selected) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)

    Box(
        modifier = Modifier
            .clip(shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .background(bg, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = text
        )
    }
}

@Composable
private fun EmptyStateCard(onAddClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        TickdownCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TickdownBadge(
                    text = "IDLE • 0 ACTIVE",
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Active Countdowns",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Track critical releases, recurring deadlines, bills, or routines with zero bloat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                TickdownButton(
                    text = "+ CREATE EVENT",
                    onClick = onAddClick,
                    isPrimary = true
                )
            }
        }
    }
}

@Composable
private fun EventCard(
    item: EventItemUiModel,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onTogglePause: () -> Unit
) {
    val event = item.event
    val details = item.details

    val accentColor = event.accentColorHex?.let {
        try {
            Color(android.graphics.Color.parseColor(it))
        } catch (_: Exception) {
            null
        }
    }

    TickdownCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        borderColor = accentColor ?: MaterialTheme.colorScheme.outline
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row: Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TickdownBadge(
                        text = formatRecurrenceLabel(event.recurrenceRule),
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TickdownStatusBadge(
                        status = event.status.name,
                        isOverdue = details.isOverdue
                    )
                }

                if (accentColor != null) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (event.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Countdown Display Box
            CountdownDigitsBox(details = details)

            Spacer(modifier = Modifier.height(14.dp))

            // Occurrence Date & Quick Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
                val dateStr = details.occurrenceDateTime.format(formatter)
                val timeStr = if (event.isTimed) {
                    " • " + details.occurrenceDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                } else ""

                Text(
                    text = "$dateStr$timeStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Pause/Resume Quick Action
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(100.dp))
                            .clickable(onClick = onTogglePause)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (event.status == EventStatus.PAUSED) "RESUME" else "PAUSE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Complete Action
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(MaterialTheme.colorScheme.secondary)
                            .clickable(onClick = onComplete)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (event.recurrenceRule is RecurrenceRule.OneTime) "DONE" else "ROLLOVER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownDigitsBox(details: OccurrenceDetails) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (details.isToday) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TODAY",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TickdownBadge(
                    text = "DUE TODAY",
                    backgroundColor = MaterialTheme.colorScheme.secondary,
                    textColor = MaterialTheme.colorScheme.onSecondary
                )
            }
        } else if (details.isDateOnly) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = String.format("%02d", details.daysRemaining),
                    style = MaterialTheme.typography.displayLarge,
                    color = if (details.isOverdue) TickdownOverdueRed else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (details.isOverdue) "DAYS OVERDUE" else "DAYS REMAINING",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DigitUnit(value = details.daysRemaining, unit = "DAYS")
                DigitSeparator()
                DigitUnit(value = details.hoursRemaining, unit = "HRS")
                DigitSeparator()
                DigitUnit(value = details.minutesRemaining, unit = "MIN")
                DigitSeparator()
                DigitUnit(value = details.secondsRemaining, unit = "SEC")
            }
        }
    }
}

@Composable
private fun DigitUnit(value: Long, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = String.format("%02d", value),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DigitSeparator() {
    Text(
        text = ":",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun formatRecurrenceLabel(rule: RecurrenceRule): String {
    return when (rule) {
        is RecurrenceRule.OneTime -> "ONE-TIME"
        is RecurrenceRule.Daily -> if (rule.interval == 1) "DAILY" else "EVERY ${rule.interval}D"
        is RecurrenceRule.Weekly -> {
            val days = rule.daysOfWeek.sorted().joinToString("") { it.name.take(1) }
            if (rule.interval == 1) "WEEKLY ($days)" else "EVERY ${rule.interval}W ($days)"
        }
        is RecurrenceRule.Monthly.DayOfMonth -> "MONTHLY (${rule.day})"
        is RecurrenceRule.Monthly.LastDay -> "MONTHLY (LAST)"
        is RecurrenceRule.Yearly -> "YEARLY (${rule.month.name.take(3)} ${rule.dayOfMonth})"
    }
}
