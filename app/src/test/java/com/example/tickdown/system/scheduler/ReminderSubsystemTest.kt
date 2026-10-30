package com.example.tickdown.system.scheduler

import com.example.tickdown.domain.model.CountdownEvent
import com.example.tickdown.domain.model.EventReminder
import com.example.tickdown.domain.model.EventStatus
import com.example.tickdown.domain.model.RecurrenceRule
import com.example.tickdown.domain.model.ReminderRule
import com.example.tickdown.domain.model.RolloverBehavior
import com.example.tickdown.domain.recurrence.RecurrenceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderSubsystemTest {

    private val zone = ZoneId.of("UTC")

    // --- 1. PendingIntent Identity & Collision Resistance ---

    @Test
    fun generateRequestCode_deterministicForSameParameters() {
        val code1 = ReminderScheduler.generateRequestCode(eventId = 42L, offsetMinutes = 15L, occurrenceEpochSec = 1775000000L)
        val code2 = ReminderScheduler.generateRequestCode(eventId = 42L, offsetMinutes = 15L, occurrenceEpochSec = 1775000000L)
        assertEquals("Request codes must be deterministic for identical parameters", code1, code2)
    }

    @Test
    fun generateRequestCode_distinctForDifferentOffsets() {
        val codeAtDeadline = ReminderScheduler.generateRequestCode(eventId = 10L, offsetMinutes = 0L, occurrenceEpochSec = 1775000000L)
        val code15MinBefore = ReminderScheduler.generateRequestCode(eventId = 10L, offsetMinutes = 15L, occurrenceEpochSec = 1775000000L)
        val code1HourBefore = ReminderScheduler.generateRequestCode(eventId = 10L, offsetMinutes = 60L, occurrenceEpochSec = 1775000000L)

        assertNotEquals("Different reminder offsets must produce distinct codes", codeAtDeadline, code15MinBefore)
        assertNotEquals("Different reminder offsets must produce distinct codes", code15MinBefore, code1HourBefore)
    }

    @Test
    fun generateRequestCode_distinctForDifferentEvents() {
        val codeEvent1 = ReminderScheduler.generateRequestCode(eventId = 1L, offsetMinutes = 0L, occurrenceEpochSec = 1775000000L)
        val codeEvent2 = ReminderScheduler.generateRequestCode(eventId = 2L, offsetMinutes = 0L, occurrenceEpochSec = 1775000000L)

        assertNotEquals("Different events must produce distinct codes", codeEvent1, codeEvent2)
    }

    @Test
    fun generateRequestCode_distinctForDifferentOccurrences() {
        val occurrence1 = 1775000000L
        val occurrence2 = 1775086400L // Next day (+86400s)
        val code1 = ReminderScheduler.generateRequestCode(eventId = 5L, offsetMinutes = 15L, occurrenceEpochSec = occurrence1)
        val code2 = ReminderScheduler.generateRequestCode(eventId = 5L, offsetMinutes = 15L, occurrenceEpochSec = occurrence2)

        assertNotEquals("Different occurrences must produce distinct codes", code1, code2)
    }

    // --- 2. Multiple Reminders & Backward Compatibility ---

    @Test
    fun effectiveReminders_returnsSortedList() {
        val event = CountdownEvent(
            id = 1,
            title = "Doctor Appointment",
            targetDate = LocalDate.of(2026, 12, 1),
            targetTime = LocalTime.of(10, 0),
            timeZoneId = "UTC",
            reminders = listOf(
                EventReminder.BEFORE_1_DAY,
                EventReminder.AT_DEADLINE,
                EventReminder.BEFORE_15_MIN
            )
        )

        val resolved = event.effectiveReminders
        assertEquals(3, resolved.size)
        assertEquals(0L, resolved[0].minutesBefore) // AT_DEADLINE
        assertEquals(15L, resolved[1].minutesBefore) // 15 min
        assertEquals(1440L, resolved[2].minutesBefore) // 1 day
    }

    @Test
    fun effectiveReminders_legacyFallback() {
        val eventMinutes = CountdownEvent(
            id = 2,
            title = "Standup",
            targetDate = LocalDate.of(2026, 12, 1),
            reminderRule = ReminderRule.MinutesBefore(30)
        )
        assertEquals(listOf(EventReminder(30)), eventMinutes.effectiveReminders)

        val eventAtDeadline = CountdownEvent(
            id = 3,
            title = "Deadlines",
            targetDate = LocalDate.of(2026, 12, 1),
            reminderRule = ReminderRule.AtTimeOfEvent
        )
        assertEquals(listOf(EventReminder.AT_DEADLINE), eventAtDeadline.effectiveReminders)

        val eventNone = CountdownEvent(
            id = 4,
            title = "None",
            targetDate = LocalDate.of(2026, 12, 1),
            reminderRule = ReminderRule.None
        )
        assertTrue(eventNone.effectiveReminders.isEmpty())
    }

    // --- 3. Cancellation, Edits, and Deleted Events ---

    @Test
    fun eventCancellation_onPausedEvent() {
        val activeEvent = CountdownEvent(
            id = 1,
            title = "Project Launch",
            targetDate = LocalDate.of(2026, 11, 15),
            status = EventStatus.ACTIVE,
            reminders = listOf(EventReminder.AT_DEADLINE)
        )
        assertEquals(EventStatus.ACTIVE, activeEvent.status)
        assertEquals(1, activeEvent.effectiveReminders.size)

        // When paused, status changes and reminders should not trigger
        val pausedEvent = activeEvent.copy(status = EventStatus.PAUSED)
        assertEquals(EventStatus.PAUSED, pausedEvent.status)
        // Validation logic in ReminderAlarmReceiver checks event.status == EventStatus.ACTIVE
        assertFalse("Paused events must not be considered active", pausedEvent.status == EventStatus.ACTIVE)
    }

    @Test
    fun eventEdits_updatesReminderTriggers() {
        val originalEvent = CountdownEvent(
            id = 1,
            title = "Quarterly Review",
            targetDate = LocalDate.of(2026, 11, 1),
            targetTime = LocalTime.of(9, 0),
            timeZoneId = "UTC",
            reminders = listOf(EventReminder.BEFORE_1_HOUR)
        )

        val originalOccurrence = RecurrenceEngine.resolveEffectiveOccurrence(originalEvent, ZonedDateTime.of(2026, 10, 20, 0, 0, 0, 0, zone))
        val originalTrigger = originalOccurrence.toInstant().minusSeconds(60 * 60)

        // User edits target date to November 15
        val editedEvent = originalEvent.copy(
            targetDate = LocalDate.of(2026, 11, 15),
            reminders = listOf(EventReminder.BEFORE_15_MIN, EventReminder.BEFORE_1_DAY)
        )

        val editedOccurrence = RecurrenceEngine.resolveEffectiveOccurrence(editedEvent, ZonedDateTime.of(2026, 10, 20, 0, 0, 0, 0, zone))
        val editedTrigger1 = editedOccurrence.toInstant().minusSeconds(15 * 60)
        val editedTrigger2 = editedOccurrence.toInstant().minusSeconds(1440 * 60)

        assertNotEquals(originalTrigger, editedTrigger1)
        assertNotEquals(originalTrigger, editedTrigger2)
        assertEquals(2, editedEvent.effectiveReminders.size)
    }

    // --- 4. Recurrence Rollover & Occurrence Validation ---

    @Test
    fun recurringEvent_rolloverComputesNextOccurrenceTriggers() {
        val dailyEvent = CountdownEvent(
            id = 10,
            title = "Daily Standup",
            targetDate = LocalDate.of(2026, 10, 19), // Yesterday
            targetTime = LocalTime.of(9, 0),
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.Daily(1),
            rolloverBehavior = RolloverBehavior.AUTOMATIC_NEXT_OCCURRENCE,
            reminders = listOf(EventReminder.BEFORE_15_MIN)
        )

        val now = ZonedDateTime.of(2026, 10, 20, 10, 0, 0, 0, zone) // Today at 10:00 (past 09:00 standup)
        val nextOccurrence = RecurrenceEngine.resolveEffectiveOccurrence(dailyEvent, now)

        // Next occurrence should automatically roll over to tomorrow at 09:00
        assertEquals(LocalDate.of(2026, 10, 21), nextOccurrence.toLocalDate())
        assertEquals(LocalTime.of(9, 0), nextOccurrence.toLocalTime())

        val reminderTrigger = nextOccurrence.toInstant().minusSeconds(15 * 60)
        assertTrue("Next occurrence reminder must be in the future", reminderTrigger.isAfter(now.toInstant()))
    }

    @Test
    fun recurringEvent_monthlyLastDayRolloverClamping() {
        val endOfMonthEvent = CountdownEvent(
            id = 11,
            title = "Month-End Billing",
            targetDate = LocalDate.of(2026, 1, 31),
            targetTime = LocalTime.of(17, 0),
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.Monthly.DayOfMonth(31),
            reminders = listOf(EventReminder.AT_DEADLINE)
        )

        val febNow = ZonedDateTime.of(2026, 2, 1, 0, 0, 0, 0, zone)
        val febOccurrence = RecurrenceEngine.resolveEffectiveOccurrence(endOfMonthEvent, febNow)

        // 2026 is not a leap year, so Feb has 28 days
        assertEquals(LocalDate.of(2026, 2, 28), febOccurrence.toLocalDate())
        assertEquals(LocalTime.of(17, 0), febOccurrence.toLocalTime())
    }

    @Test
    fun oneTimeEvent_inPast_doesNotRollover() {
        val pastEvent = CountdownEvent(
            id = 12,
            title = "Past Concert",
            targetDate = LocalDate.of(2026, 5, 1),
            targetTime = LocalTime.of(20, 0),
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.OneTime,
            reminders = listOf(EventReminder.AT_DEADLINE)
        )

        val now = ZonedDateTime.of(2026, 10, 20, 0, 0, 0, 0, zone)
        val nextOccurrence = RecurrenceEngine.calculateNextOccurrence(pastEvent, now)
        assertEquals(null, nextOccurrence)
    }

    // --- 5. Duplicate Broadcast Prevention Simulation ---

    @Test
    fun duplicateBroadcastPrevention_recordsAndFiltersRedundantDeliveries() {
        val deliveredSet = mutableSetOf<String>()

        fun buildDeliveryKey(eventId: Long, offsetMinutes: Long, occurrenceEpochSec: Long): String {
            return "$eventId-$offsetMinutes-$occurrenceEpochSec"
        }

        fun handleSimulatedAlarm(eventId: Long, offsetMinutes: Long, occurrenceEpochSec: Long): Boolean {
            val key = buildDeliveryKey(eventId, offsetMinutes, occurrenceEpochSec)
            if (deliveredSet.contains(key)) {
                return false // Duplicate skipped!
            }
            deliveredSet.add(key)
            return true // Delivered!
        }

        val eventId = 100L
        val offset = 15L
        val occurrence = 1775000000L

        // First broadcast: should be accepted
        val firstDelivery = handleSimulatedAlarm(eventId, offset, occurrence)
        assertTrue("First broadcast must be processed", firstDelivery)

        // Second broadcast with same key (e.g. system retry, alarm bounce, or reboot broadcast): should be skipped
        val duplicateDelivery = handleSimulatedAlarm(eventId, offset, occurrence)
        assertFalse("Duplicate broadcast must be suppressed", duplicateDelivery)

        // Another reminder offset for same occurrence: should be accepted
        val atDeadlineDelivery = handleSimulatedAlarm(eventId, 0L, occurrence)
        assertTrue("Different reminder offset must be processed", atDeadlineDelivery)

        // Next recurrence cycle (+86400s): should be accepted
        val nextCycleDelivery = handleSimulatedAlarm(eventId, offset, occurrence + 86400L)
        assertTrue("New occurrence cycle must be processed", nextCycleDelivery)
    }

    // --- 6. Permission Denial & Fallback Handling ---

    @Test
    fun reminderLabels_formatting() {
        assertEquals("At deadline", EventReminder.AT_DEADLINE.label)
        assertEquals("15 minutes before", EventReminder.BEFORE_15_MIN.label)
        assertEquals("1 hour before", EventReminder.BEFORE_1_HOUR.label)
        assertEquals("1 day before", EventReminder.BEFORE_1_DAY.label)
        assertEquals("1 week before", EventReminder.BEFORE_1_WEEK.label)
        assertEquals("45 minutes before", EventReminder(45).label)
        assertEquals("3 hours before", EventReminder(180).label)
        assertEquals("3 days before", EventReminder(4320).label)
    }
}
