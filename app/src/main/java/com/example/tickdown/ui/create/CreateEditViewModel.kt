package com.example.tickdown.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tickdown.domain.model.CountdownEvent
import com.example.tickdown.domain.model.EventReminder
import com.example.tickdown.domain.model.EventStatus
import com.example.tickdown.domain.model.RecurrenceRule
import com.example.tickdown.domain.model.ReminderRule
import com.example.tickdown.domain.model.RolloverBehavior
import com.example.tickdown.domain.repository.CountdownRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId

data class CreateEditUiState(
    val eventId: Long = 0L,
    val isEditMode: Boolean = false,
    val title: String = "",
    val note: String = "",
    val targetDate: LocalDate = LocalDate.now().plusDays(1),
    val hasTime: Boolean = false,
    val targetTime: LocalTime = LocalTime.of(12, 0),
    val timeZoneId: String = ZoneId.systemDefault().id,
    val recurrenceType: String = "ONE_TIME", // ONE_TIME, DAILY, WEEKLY, MONTHLY_DAY, MONTHLY_LAST_DAY, YEARLY
    val dailyInterval: Int = 1,
    val weeklyDays: Set<DayOfWeek> = setOf(LocalDate.now().dayOfWeek),
    val weeklyInterval: Int = 1,
    val monthlyDay: Int = minOf(LocalDate.now().dayOfMonth, 31),
    val yearlyMonth: Month = LocalDate.now().month,
    val yearlyDay: Int = minOf(LocalDate.now().dayOfMonth, 28),
    val rolloverBehavior: RolloverBehavior = RolloverBehavior.AUTOMATIC_NEXT_OCCURRENCE,
    val reminderRule: ReminderRule = ReminderRule.None,
    val reminders: List<EventReminder> = emptyList(),
    val accentColorHex: String? = null,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

class CreateEditViewModel(
    private val repository: CountdownRepository,
    private val eventId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateEditUiState(eventId = eventId, isEditMode = eventId > 0))
    val uiState: StateFlow<CreateEditUiState> = _uiState.asStateFlow()

    init {
        if (eventId > 0) {
            viewModelScope.launch {
                repository.getEventById(eventId)?.let { event ->
                    loadEvent(event)
                }
            }
        }
    }

    private fun loadEvent(event: CountdownEvent) {
        val (recType, dInt, wDays, wInt, mDay, yMonth, yDay) = when (val rule = event.recurrenceRule) {
            is RecurrenceRule.OneTime -> Tuple7("ONE_TIME", 1, setOf(event.targetDate.dayOfWeek), 1, event.targetDate.dayOfMonth, event.targetDate.month, event.targetDate.dayOfMonth)
            is RecurrenceRule.Daily -> Tuple7("DAILY", rule.interval, setOf(event.targetDate.dayOfWeek), 1, event.targetDate.dayOfMonth, event.targetDate.month, event.targetDate.dayOfMonth)
            is RecurrenceRule.Weekly -> Tuple7("WEEKLY", 1, rule.daysOfWeek, rule.interval, event.targetDate.dayOfMonth, event.targetDate.month, event.targetDate.dayOfMonth)
            is RecurrenceRule.Monthly.DayOfMonth -> Tuple7("MONTHLY_DAY", 1, setOf(event.targetDate.dayOfWeek), 1, rule.day, event.targetDate.month, event.targetDate.dayOfMonth)
            is RecurrenceRule.Monthly.LastDay -> Tuple7("MONTHLY_LAST_DAY", 1, setOf(event.targetDate.dayOfWeek), 1, 31, event.targetDate.month, event.targetDate.dayOfMonth)
            is RecurrenceRule.Yearly -> Tuple7("YEARLY", 1, setOf(event.targetDate.dayOfWeek), 1, 1, rule.month, rule.dayOfMonth)
        }

        _uiState.value = _uiState.value.copy(
            title = event.title,
            note = event.note,
            targetDate = event.targetDate,
            hasTime = event.targetTime != null,
            targetTime = event.targetTime ?: LocalTime.of(12, 0),
            timeZoneId = event.timeZoneId,
            recurrenceType = recType,
            dailyInterval = dInt,
            weeklyDays = wDays,
            weeklyInterval = wInt,
            monthlyDay = mDay,
            yearlyMonth = yMonth,
            yearlyDay = yDay,
            rolloverBehavior = event.rolloverBehavior,
            reminderRule = event.reminderRule,
            reminders = event.effectiveReminders,
            accentColorHex = event.accentColorHex
        )
    }

    fun onTitleChange(title: String) { _uiState.value = _uiState.value.copy(title = title, errorMessage = null) }
    fun onNoteChange(note: String) { _uiState.value = _uiState.value.copy(note = note) }
    fun onDateChange(date: LocalDate) { _uiState.value = _uiState.value.copy(targetDate = date) }
    fun onHasTimeChange(hasTime: Boolean) { _uiState.value = _uiState.value.copy(hasTime = hasTime) }
    fun onTimeChange(time: LocalTime) { _uiState.value = _uiState.value.copy(targetTime = time) }
    fun onTimeZoneChange(zone: String) { _uiState.value = _uiState.value.copy(timeZoneId = zone) }
    fun onRecurrenceTypeChange(type: String) { _uiState.value = _uiState.value.copy(recurrenceType = type) }
    fun onDailyIntervalChange(interval: Int) { _uiState.value = _uiState.value.copy(dailyInterval = maxOf(1, interval)) }
    fun onToggleWeeklyDay(day: DayOfWeek) {
        val current = _uiState.value.weeklyDays.toMutableSet()
        if (current.contains(day)) {
            if (current.size > 1) current.remove(day)
        } else {
            current.add(day)
        }
        _uiState.value = _uiState.value.copy(weeklyDays = current)
    }
    fun onWeeklyIntervalChange(interval: Int) { _uiState.value = _uiState.value.copy(weeklyInterval = maxOf(1, interval)) }
    fun onMonthlyDayChange(day: Int) { _uiState.value = _uiState.value.copy(monthlyDay = day.coerceIn(1, 31)) }
    fun onYearlyMonthChange(month: Month) { _uiState.value = _uiState.value.copy(yearlyMonth = month) }
    fun onYearlyDayChange(day: Int) { _uiState.value = _uiState.value.copy(yearlyDay = day.coerceIn(1, 31)) }
    fun onRolloverBehaviorChange(behavior: RolloverBehavior) { _uiState.value = _uiState.value.copy(rolloverBehavior = behavior) }
    fun onReminderRuleChange(rule: ReminderRule) { _uiState.value = _uiState.value.copy(reminderRule = rule) }
    fun onToggleReminder(reminder: EventReminder) {
        val current = _uiState.value.reminders.toMutableList()
        if (current.any { it.minutesBefore == reminder.minutesBefore }) {
            current.removeAll { it.minutesBefore == reminder.minutesBefore }
        } else {
            current.add(reminder)
        }
        _uiState.value = _uiState.value.copy(reminders = current.sorted())
    }
    fun addCustomReminder(reminder: EventReminder) {
        val current = _uiState.value.reminders.toMutableList()
        if (current.none { it.minutesBefore == reminder.minutesBefore }) {
            current.add(reminder)
            _uiState.value = _uiState.value.copy(reminders = current.sorted())
        }
    }
    fun removeReminder(reminder: EventReminder) {
        val current = _uiState.value.reminders.filterNot { it.minutesBefore == reminder.minutesBefore }
        _uiState.value = _uiState.value.copy(reminders = current)
    }
    fun onClearReminders() {
        _uiState.value = _uiState.value.copy(reminders = emptyList())
    }
    fun onAccentColorChange(colorHex: String?) { _uiState.value = _uiState.value.copy(accentColorHex = colorHex) }

    fun saveEvent() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Event title is required.")
            return
        }

        val recurrenceRule = when (state.recurrenceType) {
            "ONE_TIME" -> RecurrenceRule.OneTime
            "DAILY" -> RecurrenceRule.Daily(interval = state.dailyInterval)
            "WEEKLY" -> RecurrenceRule.Weekly(daysOfWeek = state.weeklyDays, interval = state.weeklyInterval)
            "MONTHLY_DAY" -> RecurrenceRule.Monthly.DayOfMonth(day = state.monthlyDay)
            "MONTHLY_LAST_DAY" -> RecurrenceRule.Monthly.LastDay
            "YEARLY" -> RecurrenceRule.Yearly(month = state.yearlyMonth, dayOfMonth = state.yearlyDay)
            else -> RecurrenceRule.OneTime
        }

        val event = CountdownEvent(
            id = state.eventId,
            title = state.title.trim(),
            note = state.note.trim(),
            targetDate = state.targetDate,
            targetTime = if (state.hasTime) state.targetTime else null,
            timeZoneId = state.timeZoneId,
            recurrenceRule = recurrenceRule,
            status = EventStatus.ACTIVE,
            accentColorHex = state.accentColorHex,
            reminderRule = if (state.reminders.isEmpty()) ReminderRule.None else ReminderRule.AtTimeOfEvent,
            reminders = state.reminders,
            rolloverBehavior = state.rolloverBehavior
        )

        viewModelScope.launch {
            if (state.isEditMode) {
                repository.updateEvent(event)
            } else {
                repository.insertEvent(event)
            }
            _uiState.value = state.copy(isSaved = true)
        }
    }

    private data class Tuple7<A, B, C, D, E, F, G>(
        val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G
    )
}
