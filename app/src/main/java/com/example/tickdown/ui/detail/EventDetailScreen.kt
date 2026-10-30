package com.example.tickdown.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.tickdown.domain.model.RolloverBehavior
import com.example.tickdown.domain.recurrence.OccurrenceDetails
import com.example.tickdown.theme.TickdownOverdueRed
import com.example.tickdown.ui.components.TickdownBadge
import com.example.tickdown.ui.components.TickdownButton
import com.example.tickdown.ui.components.TickdownCard
import com.example.tickdown.ui.components.TickdownStatusBadge
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun EventDetailScreen(
    viewModel: EventDetailViewModel,
    onBack: () -> Unit,
    onEditClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onBack()
        }
    }

    LaunchedEffect(uiState.duplicatedEventId) {
        if (uiState.duplicatedEventId != null) {
            onBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.secondary,
                        strokeWidth = 2.dp
                    )
                }
            }

            uiState.event == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Event not found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                val event = uiState.event!!
                val details = uiState.details!!

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline), CircleShape)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .clickable(onClick = onBack)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "BACK",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "EVENT DETAILS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary, CircleShape)
                                .clickable(onClick = { onEditClick(event.id) })
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "EDIT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }

                    // Main Countdown Hero Card
                    DetailHeroCard(event = event, details = details)

                    // Event Information Card
                    TickdownCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "EVENT SPECIFICATIONS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            InfoRow(
                                label = "Next Target",
                                value = formatFullDateTime(details, event.isTimed)
                            )
                            InfoRow(
                                label = "Time Zone",
                                value = event.timeZoneId
                            )
                            InfoRow(
                                label = "Recurrence",
                                value = formatRecurrenceDescription(event.recurrenceRule)
                            )
                            InfoRow(
                                label = "Rollover Policy",
                                value = if (event.rolloverBehavior == RolloverBehavior.AUTOMATIC_NEXT_OCCURRENCE) {
                                    "Automatic next cycle"
                                } else {
                                    "Requires explicit completion"
                                }
                            )

                            if (event.lastCompletedAt != null) {
                                val completedFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
                                    .withZone(event.resolvedZoneId)
                                InfoRow(
                                    label = "Last Completed",
                                    value = completedFormatter.format(event.lastCompletedAt)
                                )
                            }
                        }
                    }

                    // Primary Action: Complete / Rollover
                    TickdownButton(
                        text = if (event.recurrenceRule is RecurrenceRule.OneTime) "MARK COMPLETED" else "COMPLETE & ROLL OVER",
                        onClick = { viewModel.completeEvent() },
                        modifier = Modifier.fillMaxWidth(),
                        isPrimary = true
                    )

                    // Secondary Action Rows
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TickdownButton(
                            text = if (event.status == EventStatus.PAUSED) "RESUME" else "PAUSE",
                            onClick = { viewModel.togglePause() },
                            modifier = Modifier.weight(1f),
                            isPrimary = false
                        )
                        TickdownButton(
                            text = "DUPLICATE",
                            onClick = { viewModel.duplicateEvent() },
                            modifier = Modifier.weight(1f),
                            isPrimary = false
                        )
                    }

                    // Destructive Action: Delete
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(100.dp))
                            .border(BorderStroke(1.5.dp, TickdownOverdueRed.copy(alpha = 0.5f)), RoundedCornerShape(100.dp))
                            .clickable(onClick = { viewModel.deleteEvent() })
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DELETE EVENT",
                            style = MaterialTheme.typography.labelLarge,
                            color = TickdownOverdueRed
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailHeroCard(event: CountdownEvent, details: OccurrenceDetails) {
    val accentColor = event.accentColorHex?.let {
        try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
    }

    TickdownCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = accentColor ?: MaterialTheme.colorScheme.outline
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TickdownStatusBadge(
                    status = event.status.name,
                    isOverdue = details.isOverdue
                )

                if (accentColor != null) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = event.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (event.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = event.note,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Large Multi-Unit Digits Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 18.dp, horizontal = 12.dp)
            ) {
                if (details.isToday) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TODAY",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "TARGET DEADLINE REACHED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (details.isDateOnly) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = String.format("%02d", details.daysRemaining),
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 54.sp),
                            color = if (details.isOverdue) TickdownOverdueRed else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (details.isOverdue) "DAYS OVERDUE" else "DAYS REMAINING",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LargeDigitUnit(details.daysRemaining, "DAYS")
                        Text(":", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LargeDigitUnit(details.hoursRemaining, "HOURS")
                        Text(":", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LargeDigitUnit(details.minutesRemaining, "MINS")
                        Text(":", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LargeDigitUnit(details.secondsRemaining, "SECS")
                    }
                }
            }
        }
    }
}

@Composable
private fun LargeDigitUnit(value: Long, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = String.format("%02d", value),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatFullDateTime(details: OccurrenceDetails, isTimed: Boolean): String {
    val date = details.occurrenceDateTime.format(DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy"))
    return if (isTimed) {
        val time = details.occurrenceDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        "$date at $time"
    } else {
        "$date (All Day)"
    }
}

private fun formatRecurrenceDescription(rule: RecurrenceRule): String {
    return when (rule) {
        is RecurrenceRule.OneTime -> "One-time event"
        is RecurrenceRule.Daily -> if (rule.interval == 1) "Daily" else "Every ${rule.interval} days"
        is RecurrenceRule.Weekly -> {
            val days = rule.daysOfWeek.sorted().joinToString(", ") { it.name.take(3) }
            if (rule.interval == 1) "Weekly on $days" else "Every ${rule.interval} weeks on $days"
        }
        is RecurrenceRule.Monthly.DayOfMonth -> "Monthly on day ${rule.day}"
        is RecurrenceRule.Monthly.LastDay -> "Monthly on last calendar day"
        is RecurrenceRule.Yearly -> "Yearly on ${rule.month.name} ${rule.dayOfMonth}"
    }
}
