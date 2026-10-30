package com.example.tickdown.ui.create

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tickdown.domain.model.EventReminder
import com.example.tickdown.domain.model.ReminderRule
import com.example.tickdown.domain.model.RolloverBehavior
import com.example.tickdown.system.scheduler.ReminderScheduler
import com.example.tickdown.ui.components.TickdownBadge
import com.example.tickdown.ui.components.TickdownButton
import com.example.tickdown.ui.components.TickdownCard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateEditScreen(
    viewModel: CreateEditViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasExactAlarmPermission by remember {
        mutableStateOf(ReminderScheduler.canScheduleExactAlarms(context))
    }

    var showCustomReminderDialog by remember { mutableStateOf(false) }
    var customReminderQuantity by remember { mutableStateOf("30") }
    var customReminderUnit by remember { mutableStateOf("Minutes") }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        hasExactAlarmPermission = ReminderScheduler.canScheduleExactAlarms(context)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onBack()
        }
    }

    val showDatePicker = {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                viewModel.onDateChange(LocalDate.of(year, month + 1, dayOfMonth))
            },
            uiState.targetDate.year,
            uiState.targetDate.monthValue - 1,
            uiState.targetDate.dayOfMonth
        ).show()
    }

    val showTimePicker = {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                viewModel.onTimeChange(LocalTime.of(hourOfDay, minute))
            },
            uiState.targetTime.hour,
            uiState.targetTime.minute,
            true
        ).show()
    }

    if (showCustomReminderDialog) {
        val parsedQty = customReminderQuantity.filter { it.isDigit() }.toLongOrNull() ?: 1L
        val calculatedMinutes = when (customReminderUnit) {
            "Minutes" -> parsedQty
            "Hours" -> parsedQty * 60L
            "Days" -> parsedQty * 1440L
            "Weeks" -> parsedQty * 10080L
            else -> parsedQty
        }

        AlertDialog(
            onDismissRequest = { showCustomReminderDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "CUSTOM REMINDER",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Specify any custom lead time before the event deadline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = customReminderQuantity,
                        onValueChange = { customReminderQuantity = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Text(
                        text = "UNIT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Minutes", "Hours", "Days", "Weeks").forEach { unit ->
                            QuickDateChip(
                                label = unit.uppercase(),
                                isSelected = customReminderUnit == unit
                            ) {
                                customReminderUnit = unit
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Summary: ${EventReminder(calculatedMinutes).label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        viewModel.addCustomReminder(EventReminder(calculatedMinutes))
                        showCustomReminderDialog = false
                    }
                ) {
                    Text("ADD REMINDER", color = MaterialTheme.colorScheme.secondary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomReminderDialog = false }) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                TickdownButton(
                    text = if (uiState.isEditMode) "SAVE CHANGES" else "CREATE COUNTDOWN",
                    onClick = { viewModel.saveEvent() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (uiState.isEditMode) "Edit Event" else "New Event",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "CONFIGURE TARGET & RECURRENCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline), CircleShape)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .clickable(onClick = onBack)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "CANCEL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Title & Note Card
            TickdownCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "EVENT DETAILS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = uiState.title,
                        onValueChange = { viewModel.onTitleChange(it) },
                        label = { Text("Title", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    OutlinedTextField(
                        value = uiState.note,
                        onValueChange = { viewModel.onNoteChange(it) },
                        label = { Text("Note / Description (Optional)", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }

            // Target Date & Time Card
            TickdownCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "SCHEDULE & TIME",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Interactive Selected Date Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { showDatePicker() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Selected Date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")
                            Text(
                                text = uiState.targetDate.format(dateFormatter),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "📅 CHANGE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // Quick & Custom Date Pickers
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val today = LocalDate.now()
                        QuickDateChip(label = "TODAY", isSelected = uiState.targetDate == today) {
                            viewModel.onDateChange(today)
                        }
                        QuickDateChip(label = "+1 DAY", isSelected = uiState.targetDate == today.plusDays(1)) {
                            viewModel.onDateChange(today.plusDays(1))
                        }
                        QuickDateChip(label = "+1 WEEK", isSelected = uiState.targetDate == today.plusWeeks(1)) {
                            viewModel.onDateChange(today.plusWeeks(1))
                        }
                        QuickDateChip(label = "+1 MONTH", isSelected = uiState.targetDate == today.plusMonths(1)) {
                            viewModel.onDateChange(today.plusMonths(1))
                        }
                        QuickDateChip(label = "+ PICK ANY DATE", isSelected = false) {
                            showDatePicker()
                        }
                    }

                    // Include Time Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Specific Time",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (uiState.hasTime) "Timed event (exact hour:minute countdown)" else "Date-only event (active until day ends)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = uiState.hasTime,
                            onCheckedChange = { viewModel.onHasTimeChange(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.secondary,
                                checkedTrackColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }

                    if (uiState.hasTime) {
                        // Interactive Target Time Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { showTimePicker() }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Target Time (24h)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format("%02d:%02d", uiState.targetTime.hour, uiState.targetTime.minute),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "⏰ CHANGE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QuickTimeChip(label = "09:00") { viewModel.onTimeChange(LocalTime.of(9, 0)) }
                            QuickTimeChip(label = "12:00") { viewModel.onTimeChange(LocalTime.of(12, 0)) }
                            QuickTimeChip(label = "18:00") { viewModel.onTimeChange(LocalTime.of(18, 0)) }
                            QuickTimeChip(label = "23:59") { viewModel.onTimeChange(LocalTime.of(23, 59)) }
                            QuickDateChip(label = "+ PICK TIME", isSelected = false) { showTimePicker() }
                        }

                        // Timezone Display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Time Zone",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TickdownBadge(
                                text = uiState.timeZoneId,
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Recurrence Rule Card
            TickdownCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "RECURRENCE ENGINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RecurrenceChip(label = "ONE-TIME", selected = uiState.recurrenceType == "ONE_TIME") {
                            viewModel.onRecurrenceTypeChange("ONE_TIME")
                        }
                        RecurrenceChip(label = "DAILY", selected = uiState.recurrenceType == "DAILY") {
                            viewModel.onRecurrenceTypeChange("DAILY")
                        }
                        RecurrenceChip(label = "WEEKLY", selected = uiState.recurrenceType == "WEEKLY") {
                            viewModel.onRecurrenceTypeChange("WEEKLY")
                        }
                        RecurrenceChip(label = "MONTHLY (DAY)", selected = uiState.recurrenceType == "MONTHLY_DAY") {
                            viewModel.onRecurrenceTypeChange("MONTHLY_DAY")
                        }
                        RecurrenceChip(label = "MONTHLY (LAST)", selected = uiState.recurrenceType == "MONTHLY_LAST_DAY") {
                            viewModel.onRecurrenceTypeChange("MONTHLY_LAST_DAY")
                        }
                        RecurrenceChip(label = "YEARLY", selected = uiState.recurrenceType == "YEARLY") {
                            viewModel.onRecurrenceTypeChange("YEARLY")
                        }
                    }

                    // Recurrence specific controls
                    when (uiState.recurrenceType) {
                        "DAILY" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(text = "Repeat Every:", style = MaterialTheme.typography.bodyMedium)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    QuickDateChip("1 DAY", uiState.dailyInterval == 1) { viewModel.onDailyIntervalChange(1) }
                                    QuickDateChip("2 DAYS", uiState.dailyInterval == 2) { viewModel.onDailyIntervalChange(2) }
                                    QuickDateChip("7 DAYS", uiState.dailyInterval == 7) { viewModel.onDailyIntervalChange(7) }
                                    QuickDateChip("14 DAYS", uiState.dailyInterval == 14) { viewModel.onDailyIntervalChange(14) }
                                    QuickDateChip("30 DAYS", uiState.dailyInterval == 30) { viewModel.onDailyIntervalChange(30) }
                                }
                                OutlinedTextField(
                                    value = uiState.dailyInterval.toString(),
                                    onValueChange = { str ->
                                        str.filter { it.isDigit() }.toIntOrNull()?.let {
                                            viewModel.onDailyIntervalChange(it)
                                        }
                                    },
                                    label = { Text("Custom days interval") },
                                    suffix = { Text("days") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }

                        "WEEKLY" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Select Weekdays:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    DayOfWeek.values().forEach { dow ->
                                        val isSelected = uiState.weeklyDays.contains(dow)
                                        val shortName = dow.name.take(1)
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                                    CircleShape
                                                )
                                                .clickable { viewModel.onToggleWeeklyDay(dow) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = shortName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "Repeat Every:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    QuickDateChip("1 WK", uiState.weeklyInterval == 1) { viewModel.onWeeklyIntervalChange(1) }
                                    QuickDateChip("2 WKS", uiState.weeklyInterval == 2) { viewModel.onWeeklyIntervalChange(2) }
                                    QuickDateChip("3 WKS", uiState.weeklyInterval == 3) { viewModel.onWeeklyIntervalChange(3) }
                                    QuickDateChip("4 WKS", uiState.weeklyInterval == 4) { viewModel.onWeeklyIntervalChange(4) }
                                }
                                OutlinedTextField(
                                    value = uiState.weeklyInterval.toString(),
                                    onValueChange = { str ->
                                        str.filter { it.isDigit() }.toIntOrNull()?.let {
                                            viewModel.onWeeklyIntervalChange(it)
                                        }
                                    },
                                    label = { Text("Custom weeks interval") },
                                    suffix = { Text("weeks") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }

                        "MONTHLY_DAY" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Repeats on day ${uiState.monthlyDay} of every month.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "If scheduled on the 31st (or 30th/29th), shorter months like February will safely clamp to the last calendar day without altering your original 31st rule.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    QuickDateChip("1st", uiState.monthlyDay == 1) { viewModel.onMonthlyDayChange(1) }
                                    QuickDateChip("15th", uiState.monthlyDay == 15) { viewModel.onMonthlyDayChange(15) }
                                    QuickDateChip("28th", uiState.monthlyDay == 28) { viewModel.onMonthlyDayChange(28) }
                                    QuickDateChip("31st", uiState.monthlyDay == 31) { viewModel.onMonthlyDayChange(31) }
                                }

                                OutlinedTextField(
                                    value = uiState.monthlyDay.toString(),
                                    onValueChange = { str ->
                                        str.filter { it.isDigit() }.toIntOrNull()?.let {
                                            if (it in 1..31) viewModel.onMonthlyDayChange(it)
                                        }
                                    },
                                    label = { Text("Custom day of month (1 to 31)") },
                                    suffix = { Text("day") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }

                        "MONTHLY_LAST_DAY" -> {
                            Text(
                                text = "Always recurs on the final calendar day of each month (28th/29th in Feb, 30th or 31st).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        "YEARLY" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Repeats every year on ${uiState.yearlyMonth.name} ${uiState.yearlyDay}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                OutlinedTextField(
                                    value = uiState.yearlyDay.toString(),
                                    onValueChange = { str ->
                                        str.filter { it.isDigit() }.toIntOrNull()?.let {
                                            if (it in 1..31) viewModel.onYearlyDayChange(it)
                                        }
                                    },
                                    label = { Text("Day of month (1 to 31)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Rollover Behavior Card
            TickdownCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "PASSED OCCURRENCE BEHAVIOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RecurrenceChip(
                            label = "AUTO ROLLOVER",
                            selected = uiState.rolloverBehavior == RolloverBehavior.AUTOMATIC_NEXT_OCCURRENCE
                        ) {
                            viewModel.onRolloverBehaviorChange(RolloverBehavior.AUTOMATIC_NEXT_OCCURRENCE)
                        }

                        RecurrenceChip(
                            label = "REQUIRE COMPLETION",
                            selected = uiState.rolloverBehavior == RolloverBehavior.REQUIRE_MANUAL_COMPLETION
                        ) {
                            viewModel.onRolloverBehaviorChange(RolloverBehavior.REQUIRE_MANUAL_COMPLETION)
                        }
                    }

                    Text(
                        text = if (uiState.rolloverBehavior == RolloverBehavior.AUTOMATIC_NEXT_OCCURRENCE) {
                            "When the deadline passes, automatically counts down to the next recurrence cycle."
                        } else {
                            "When the deadline passes, remains in an OVERDUE state until you explicitly mark it complete."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Reminders & Alerts Card
            TickdownCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "REMINDERS & ALERTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (uiState.reminders.isNotEmpty()) {
                            Text(
                                text = "CLEAR ALL",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { viewModel.onClearReminders() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "Configure multiple alerts before and at the event deadline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Multi-reminder Chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val presets = listOf(
                            EventReminder.AT_DEADLINE,
                            EventReminder.BEFORE_15_MIN,
                            EventReminder.BEFORE_1_HOUR,
                            EventReminder.BEFORE_1_DAY,
                            EventReminder.BEFORE_1_WEEK
                        )

                        // Standard preset chips
                        presets.forEach { reminder ->
                            val isSelected = uiState.reminders.any { it.minutesBefore == reminder.minutesBefore }
                            RecurrenceChip(
                                label = reminder.label.uppercase(),
                                selected = isSelected
                            ) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                viewModel.onToggleReminder(reminder)
                            }
                        }

                        // Custom added reminders not in presets
                        uiState.reminders.filterNot { r -> presets.any { it.minutesBefore == r.minutesBefore } }.forEach { customRem ->
                            RecurrenceChip(
                                label = "${customRem.label.uppercase()} ✕",
                                selected = true
                            ) {
                                viewModel.removeReminder(customRem)
                            }
                        }

                        // + CUSTOM REMINDER trigger
                        RecurrenceChip(
                            label = "+ CUSTOM REMINDER",
                            selected = false
                        ) {
                            showCustomReminderDialog = true
                        }
                    }

                    // Selected Reminders Summary
                    if (uiState.reminders.isNotEmpty()) {
                        Text(
                            text = "Active: " + uiState.reminders.joinToString(", ") { it.label },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Inexact Alarm Fallback Explanation
                    if (!hasExactAlarmPermission) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "⚠️ Exact alarm access is not granted. Alerts may be delayed by Android battery optimization.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Text(
                                        text = "GRANT IN SETTINGS →",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier
                                            .clickable {
                                                try {
                                                    val intent = Intent(
                                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                        Uri.parse("package:${context.packageName}")
                                                    )
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {
                                                    val intent = Intent(
                                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                        Uri.parse("package:${context.packageName}")
                                                    )
                                                    context.startActivity(intent)
                                                }
                                            }
                                            .padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Accent Color Card
            TickdownCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ACCENT COLOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ColorDot(color = null, isSelected = uiState.accentColorHex == null) {
                            viewModel.onAccentColorChange(null)
                        }
                        ColorDot(color = Color(0xFF2563EB), isSelected = uiState.accentColorHex == "#2563EB") {
                            viewModel.onAccentColorChange("#2563EB")
                        }
                        ColorDot(color = Color(0xFF0EA5E9), isSelected = uiState.accentColorHex == "#0EA5E9") {
                            viewModel.onAccentColorChange("#0EA5E9")
                        }
                        ColorDot(color = Color(0xFF4F46E5), isSelected = uiState.accentColorHex == "#4F46E5") {
                            viewModel.onAccentColorChange("#4F46E5")
                        }
                        ColorDot(color = Color(0xFFD97706), isSelected = uiState.accentColorHex == "#D97706") {
                            viewModel.onAccentColorChange("#D97706")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun QuickDateChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .border(
                BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                ),
                RoundedCornerShape(100.dp)
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(100.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun QuickTimeChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecurrenceChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
private fun ColorDot(color: Color?, isSelected: Boolean, onClick: () -> Unit) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .border(
                BorderStroke(
                    if (isSelected) 2.5.dp else 1.dp,
                    if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                ),
                shape
            )
            .background(color ?: MaterialTheme.colorScheme.surface, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (color == null) {
            Text(
                text = "M",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
