package com.example.tickdown.domain.recurrence

import com.example.tickdown.domain.model.CountdownEvent
import com.example.tickdown.domain.model.EventReminder
import com.example.tickdown.domain.model.EventStatus
import com.example.tickdown.domain.model.RecurrenceRule
import com.example.tickdown.domain.model.RolloverBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Dedicated QA suite verifying monthly recurrences, leap-year handling,
 * event completion, and reminder state transitions.
 */
class MonthlyScenariosTest {

    private val utcZone = ZoneId.of("UTC")

    // --- Scenario 1: Four Monthly Events & Their Next Occurrences ---

    @Test
    fun fourMonthlyEvents_verifyNextOccurrencesAcrossMonths() {
        // Event 1: 1st of the month (e.g. Rent)
        val event1 = CountdownEvent(
            id = 1,
            title = "Rent Payment",
            targetDate = LocalDate.of(2026, 1, 1),
            targetTime = LocalTime.of(9, 0),
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.Monthly.DayOfMonth(1)
        )

        // Event 2: 15th of the month (e.g. Mid-month check-in)
        val event2 = CountdownEvent(
            id = 2,
            title = "Mid-Month Sync",
            targetDate = LocalDate.of(2026, 1, 15),
            targetTime = LocalTime.of(14, 0),
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.Monthly.DayOfMonth(15)
        )

        // Event 3: 31st of the month (Fixed day with clamping)
        val event3 = CountdownEvent(
            id = 3,
            title = "Month-End Review (31st)",
            targetDate = LocalDate.of(2026, 1, 31),
            targetTime = LocalTime.of(17, 0),
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.Monthly.DayOfMonth(31)
        )

        // Event 4: Last calendar day of the month (Dynamic last day)
        val event4 = CountdownEvent(
            id = 4,
            title = "Final Invoice (Last Day)",
            targetDate = LocalDate.of(2026, 1, 31),
            targetTime = LocalTime.of(18, 0),
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.Monthly.LastDay
        )

        // Reference: February 10, 2026
        val refFeb = ZonedDateTime.of(2026, 2, 10, 0, 0, 0, 0, utcZone)

        // Verify Event 1 (1st): Next is March 1, 2026 (since Feb 1 has passed)
        val next1 = RecurrenceEngine.calculateNextOccurrence(event1, refFeb)
        assertNotNull(next1)
        assertEquals(LocalDate.of(2026, 3, 1), next1!!.toLocalDate())

        // Verify Event 2 (15th): Next is February 15, 2026 (upcoming in 5 days)
        val next2 = RecurrenceEngine.calculateNextOccurrence(event2, refFeb)
        assertNotNull(next2)
        assertEquals(LocalDate.of(2026, 2, 15), next2!!.toLocalDate())

        // Verify Event 3 (31st): Clamped to February 28, 2026 (2026 non-leap year)
        val next3 = RecurrenceEngine.calculateNextOccurrence(event3, refFeb)
        assertNotNull(next3)
        assertEquals(LocalDate.of(2026, 2, 28), next3!!.toLocalDate())

        // Verify Event 4 (LastDay): Evaluates to February 28, 2026
        val next4 = RecurrenceEngine.calculateNextOccurrence(event4, refFeb)
        assertNotNull(next4)
        assertEquals(LocalDate.of(2026, 2, 28), next4!!.toLocalDate())

        // Move timeline to March 2, 2026
        val refMar = ZonedDateTime.of(2026, 3, 2, 0, 0, 0, 0, utcZone)
        // Event 3 (31st) in March must revert to 31st (not stay clamped at 28th!)
        val next3Mar = RecurrenceEngine.calculateNextOccurrence(event3, refMar)
        assertNotNull(next3Mar)
        assertEquals(LocalDate.of(2026, 3, 31), next3Mar!!.toLocalDate())

        // Event 4 (LastDay) in March must evaluate to March 31
        val next4Mar = RecurrenceEngine.calculateNextOccurrence(event4, refMar)
        assertNotNull(next4Mar)
        assertEquals(LocalDate.of(2026, 3, 31), next4Mar!!.toLocalDate())

        // Move timeline to April 2, 2026
        val refApr = ZonedDateTime.of(2026, 4, 2, 0, 0, 0, 0, utcZone)
        // Event 3 (31st) in April (30 days) must clamp to April 30
        val next3Apr = RecurrenceEngine.calculateNextOccurrence(event3, refApr)
        assertNotNull(next3Apr)
        assertEquals(LocalDate.of(2026, 4, 30), next3Apr!!.toLocalDate())
    }

    // --- Scenario 5: Complete Recurring Occurrence & Next Occurrence ---

    @Test
    fun completeRecurringOccurrence_advancesCorrectlyPreservingRule() {
        val event = CountdownEvent(
            id = 10,
            title = "Bi-weekly Clean",
            targetDate = LocalDate.of(2026, 10, 15),
            targetTime = LocalTime.of(10, 0),
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.Weekly(
                daysOfWeek = setOf(java.time.DayOfWeek.THURSDAY),
                interval = 2
            ),
            status = EventStatus.ACTIVE,
            rolloverBehavior = RolloverBehavior.REQUIRE_MANUAL_COMPLETION
        )

        // User completes event on Oct 16
        val completionInstant = ZonedDateTime.of(2026, 10, 16, 12, 0, 0, 0, utcZone).toInstant()
        val advanced = RecurrenceEngine.advanceToNextOccurrence(event, completionInstant)

        assertEquals("Status must remain ACTIVE for recurring event", EventStatus.ACTIVE, advanced.status)
        assertEquals(LocalDate.of(2026, 10, 29), advanced.targetDate)
        assertEquals(LocalTime.of(10, 0), advanced.targetTime)
        assertEquals(completionInstant, advanced.lastCompletedAt)
        assertTrue(advanced.recurrenceRule is RecurrenceRule.Weekly)
    }

    @Test
    fun completeOneTimeEvent_marksAsCompleted() {
        val oneTime = CountdownEvent(
            id = 20,
            title = "One-time Seminar",
            targetDate = LocalDate.of(2026, 11, 20),
            recurrenceRule = RecurrenceRule.OneTime,
            status = EventStatus.ACTIVE
        )

        val completionInstant = ZonedDateTime.of(2026, 11, 20, 21, 0, 0, 0, utcZone).toInstant()
        val advanced = RecurrenceEngine.advanceToNextOccurrence(oneTime, completionInstant)

        assertEquals("One-time events must transition to COMPLETED", EventStatus.COMPLETED, advanced.status)
        assertEquals(completionInstant, advanced.lastCompletedAt)
    }

    // --- Scenario 4: Edit or Delete Event After Reminder Scheduled ---

    @Test
    fun editOrDeleteEvent_remindersIntegrity() {
        var scheduledAlarms = mutableMapOf<Long, List<EventReminder>>()

        fun schedule(event: CountdownEvent) {
            scheduledAlarms[event.id] = event.effectiveReminders
        }

        fun cancel(event: CountdownEvent) {
            scheduledAlarms.remove(event.id)
        }

        val event = CountdownEvent(
            id = 99,
            title = "Sprint Planning",
            targetDate = LocalDate.of(2026, 11, 1),
            reminders = listOf(EventReminder.AT_DEADLINE, EventReminder.BEFORE_1_HOUR)
        )

        // Initially scheduled
        schedule(event)
        assertEquals(2, scheduledAlarms[99]?.size)

        // Edit event: add another reminder
        val edited = event.copy(
            reminders = listOf(EventReminder.AT_DEADLINE, EventReminder.BEFORE_15_MIN, EventReminder.BEFORE_1_HOUR)
        )
        // Scheduler update replaces scheduled alarms
        schedule(edited)
        assertEquals(3, scheduledAlarms[99]?.size)

        // Delete event
        cancel(edited)
        assertFalse("Alarms must be removed after deletion", scheduledAlarms.containsKey(99))
    }
}
