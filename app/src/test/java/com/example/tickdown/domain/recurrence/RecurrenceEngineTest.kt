package com.example.tickdown.domain.recurrence

import com.example.tickdown.domain.model.CountdownEvent
import com.example.tickdown.domain.model.EventStatus
import com.example.tickdown.domain.model.RecurrenceRule
import com.example.tickdown.domain.model.RolloverBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurrenceEngineTest {

    private val utcZone = ZoneId.of("UTC")
    private val nyZone = ZoneId.of("America/New_York")
    private val tokyoZone = ZoneId.of("Asia/Tokyo")

    @Test
    fun oneTimeEvent_inFuture_returnsTargetDateTime() {
        val event = CountdownEvent(
            title = "Launch Party",
            targetDate = LocalDate.of(2026, 12, 31),
            targetTime = LocalTime.of(20, 0),
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.OneTime
        )

        val reference = ZonedDateTime.of(2026, 10, 20, 10, 0, 0, 0, utcZone)
        val next = RecurrenceEngine.calculateNextOccurrence(event, reference)

        assertNotNull(next)
        assertEquals(ZonedDateTime.of(2026, 12, 31, 20, 0, 0, 0, utcZone), next)
    }

    @Test
    fun oneTimeEvent_inPast_returnsNull() {
        val event = CountdownEvent(
            title = "Old Anniversary",
            targetDate = LocalDate.of(2025, 1, 1),
            targetTime = LocalTime.of(12, 0),
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.OneTime
        )

        val reference = ZonedDateTime.of(2026, 10, 20, 10, 0, 0, 0, utcZone)
        val next = RecurrenceEngine.calculateNextOccurrence(event, reference)

        assertNull(next)
    }

    @Test
    fun dailyEvent_advancesByInterval() {
        val event = CountdownEvent(
            title = "Daily Standup",
            targetDate = LocalDate.of(2026, 10, 1),
            targetTime = LocalTime.of(9, 30),
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.Daily(interval = 2)
        )

        // Reference is Oct 20 at 10:00 -> anchor Oct 1 was 19 days ago (odd).
        // Multiples of 2 from Oct 1: Oct 1, Oct 3, ... Oct 19, Oct 21.
        val reference = ZonedDateTime.of(2026, 10, 20, 10, 0, 0, 0, utcZone)
        val next = RecurrenceEngine.calculateNextOccurrence(event, reference)

        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 10, 21), next!!.toLocalDate())
        assertEquals(LocalTime.of(9, 30), next.toLocalTime())
    }

    @Test
    fun weeklyEvent_multipleDays_picksNextMatchingDayInOrder() {
        // Mon, Wed, Fri weekly standup
        val event = CountdownEvent(
            title = "Team Sync",
            targetDate = LocalDate.of(2026, 10, 19), // Monday
            targetTime = LocalTime.of(10, 0),
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.Weekly(
                daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                interval = 1
            )
        )

        // If reference is Tuesday Oct 20 -> next should be Wednesday Oct 21
        val refTuesday = ZonedDateTime.of(2026, 10, 20, 12, 0, 0, 0, utcZone)
        val nextWed = RecurrenceEngine.calculateNextOccurrence(event, refTuesday)
        assertNotNull(nextWed)
        assertEquals(LocalDate.of(2026, 10, 21), nextWed!!.toLocalDate())

        // If reference is Friday Oct 23 at 11:00 (past 10:00) -> next should be Monday Oct 26
        val refFriPast = ZonedDateTime.of(2026, 10, 23, 11, 0, 0, 0, utcZone)
        val nextMon = RecurrenceEngine.calculateNextOccurrence(event, refFriPast)
        assertNotNull(nextMon)
        assertEquals(LocalDate.of(2026, 10, 26), nextMon!!.toLocalDate())
    }

    @Test
    fun monthlyEvent_scheduledOn31st_clampsInShorterMonthsWithoutMutatingRule() {
        val rule = RecurrenceRule.Monthly.DayOfMonth(day = 31)
        val event = CountdownEvent(
            title = "End of Month Review",
            targetDate = LocalDate.of(2026, 1, 31),
            targetTime = LocalTime.of(17, 0),
            timeZoneId = "UTC",
            recurrenceRule = rule
        )

        // 1. In February 2026 (non-leap year), 31st must clamp to Feb 28
        val refFeb = ZonedDateTime.of(2026, 2, 1, 0, 0, 0, 0, utcZone)
        val nextFeb = RecurrenceEngine.calculateNextOccurrence(event, refFeb)
        assertNotNull(nextFeb)
        assertEquals(LocalDate.of(2026, 2, 28), nextFeb!!.toLocalDate())

        // Verify rule is not mutated
        assertEquals(31, (event.recurrenceRule as RecurrenceRule.Monthly.DayOfMonth).day)

        // 2. In March 2026 (31 days), it must use March 31st (not 28th!)
        val refMar = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, utcZone)
        val nextMar = RecurrenceEngine.calculateNextOccurrence(event, refMar)
        assertNotNull(nextMar)
        assertEquals(LocalDate.of(2026, 3, 31), nextMar!!.toLocalDate())

        // 3. In April 2026 (30 days), it must clamp to April 30th
        val refApr = ZonedDateTime.of(2026, 4, 1, 0, 0, 0, 0, utcZone)
        val nextApr = RecurrenceEngine.calculateNextOccurrence(event, refApr)
        assertNotNull(nextApr)
        assertEquals(LocalDate.of(2026, 4, 30), nextApr!!.toLocalDate())
    }

    @Test
    fun monthlyEvent_on31st_inLeapYearFebruary_uses29th() {
        val event = CountdownEvent(
            title = "Leap Review",
            targetDate = LocalDate.of(2024, 1, 31),
            targetTime = LocalTime.of(12, 0),
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.Monthly.DayOfMonth(day = 31)
        )

        // 2024 is a leap year
        val refFebLeap = ZonedDateTime.of(2024, 2, 1, 0, 0, 0, 0, utcZone)
        val nextFebLeap = RecurrenceEngine.calculateNextOccurrence(event, refFebLeap)
        assertNotNull(nextFebLeap)
        assertEquals(LocalDate.of(2024, 2, 29), nextFebLeap!!.toLocalDate())
    }

    @Test
    fun monthlyEvent_lastDayOfMonth_alwaysEvaluatesToEndOfMonth() {
        val event = CountdownEvent(
            title = "Monthly Invoice",
            targetDate = LocalDate.of(2026, 1, 1),
            targetTime = LocalTime.of(9, 0),
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.Monthly.LastDay
        )

        val refFeb = ZonedDateTime.of(2026, 2, 1, 0, 0, 0, 0, utcZone)
        val nextFeb = RecurrenceEngine.calculateNextOccurrence(event, refFeb)
        assertEquals(LocalDate.of(2026, 2, 28), nextFeb!!.toLocalDate())

        val refApr = ZonedDateTime.of(2026, 4, 1, 0, 0, 0, 0, utcZone)
        val nextApr = RecurrenceEngine.calculateNextOccurrence(event, refApr)
        assertEquals(LocalDate.of(2026, 4, 30), nextApr!!.toLocalDate())
    }

    @Test
    fun yearlyEvent_onLeapDay_Feb29_adjustsToFeb28InNonLeapYears() {
        val event = CountdownEvent(
            title = "Leap Birthday",
            targetDate = LocalDate.of(2024, 2, 29),
            targetTime = LocalTime.of(10, 0),
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.Yearly(Month.FEBRUARY, 29)
        )

        // 2025 is not a leap year -> Feb 28
        val ref2025 = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, utcZone)
        val next2025 = RecurrenceEngine.calculateNextOccurrence(event, ref2025)
        assertNotNull(next2025)
        assertEquals(LocalDate.of(2025, 2, 28), next2025!!.toLocalDate())

        // 2028 is a leap year -> Feb 29
        val ref2028 = ZonedDateTime.of(2028, 1, 1, 0, 0, 0, 0, utcZone)
        val next2028 = RecurrenceEngine.calculateNextOccurrence(event, ref2028)
        assertNotNull(next2028)
        assertEquals(LocalDate.of(2028, 2, 29), next2028!!.toLocalDate())

        // Rule is still Feb 29
        assertEquals(29, (event.recurrenceRule as RecurrenceRule.Yearly).dayOfMonth)
    }

    @Test
    fun dateOnlyEvent_semantics_treatedAsActiveAllDayUntilMidnightPasses() {
        val event = CountdownEvent(
            title = "All Day Release",
            targetDate = LocalDate.of(2026, 10, 20),
            targetTime = null, // Date-only
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.OneTime
        )

        // At 14:00 on the same date: date-only is NOT past (it is TODAY)
        val duringDay = ZonedDateTime.of(2026, 10, 20, 14, 0, 0, 0, utcZone)
        assertFalse(RecurrenceEngine.isPast(RecurrenceEngine.getAnchorZonedDateTime(event), isDateOnly = true, duringDay))

        val details = RecurrenceEngine.calculateOccurrenceDetails(event, duringDay)
        assertTrue(details.isToday)
        assertFalse(details.isOverdue)
        assertEquals(0L, details.daysRemaining)

        // On next day Oct 21 at 00:01: date-only has passed
        val nextDay = ZonedDateTime.of(2026, 10, 21, 0, 1, 0, 0, utcZone)
        assertTrue(RecurrenceEngine.isPast(RecurrenceEngine.getAnchorZonedDateTime(event), isDateOnly = true, nextDay))
    }

    @Test
    fun timedEvent_semantics_passesExactlyAtSpecifiedTime() {
        val event = CountdownEvent(
            title = "Webinar",
            targetDate = LocalDate.of(2026, 10, 20),
            targetTime = LocalTime.of(14, 0),
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.OneTime
        )

        val before = ZonedDateTime.of(2026, 10, 20, 13, 59, 0, 0, utcZone)
        assertFalse(RecurrenceEngine.isPast(RecurrenceEngine.getAnchorZonedDateTime(event), isDateOnly = false, before))

        val after = ZonedDateTime.of(2026, 10, 20, 14, 1, 0, 0, utcZone)
        assertTrue(RecurrenceEngine.isPast(RecurrenceEngine.getAnchorZonedDateTime(event), isDateOnly = false, after))
    }

    @Test
    fun rolloverBehavior_requireManualCompletion_remainsOverdueUntilExplicitlyCompleted() {
        val event = CountdownEvent(
            title = "Rent Payment",
            targetDate = LocalDate.of(2026, 10, 1),
            targetTime = LocalTime.of(12, 0),
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.Monthly.DayOfMonth(1),
            rolloverBehavior = RolloverBehavior.REQUIRE_MANUAL_COMPLETION
        )

        // On Oct 15, rent was due on Oct 1.
        val now = ZonedDateTime.of(2026, 10, 15, 12, 0, 0, 0, utcZone)
        val effective = RecurrenceEngine.resolveEffectiveOccurrence(event, now)

        // It should NOT silently advance to Nov 1!
        assertEquals(LocalDate.of(2026, 10, 1), effective.toLocalDate())

        val details = RecurrenceEngine.calculateOccurrenceDetails(event, now)
        assertTrue(details.isOverdue)

        // Now user explicitly clicks "Complete":
        val advancedEvent = RecurrenceEngine.advanceToNextOccurrence(event, now.toInstant())
        assertEquals(LocalDate.of(2026, 11, 1), advancedEvent.targetDate)
        assertEquals(EventStatus.ACTIVE, advancedEvent.status)
        assertNotNull(advancedEvent.lastCompletedAt)
    }

    @Test
    fun rolloverBehavior_automatic_rollsOverToNextOccurrenceDynamically() {
        val event = CountdownEvent(
            title = "Weekly Trash Day",
            targetDate = LocalDate.of(2026, 10, 14), // Wednesday
            targetTime = LocalTime.of(8, 0),
            timeZoneId = "UTC",
            recurrenceRule = RecurrenceRule.Weekly(setOf(DayOfWeek.WEDNESDAY), interval = 1),
            rolloverBehavior = RolloverBehavior.AUTOMATIC_NEXT_OCCURRENCE
        )

        // On Friday Oct 16: automatically shows next Wednesday Oct 21
        val now = ZonedDateTime.of(2026, 10, 16, 12, 0, 0, 0, utcZone)
        val effective = RecurrenceEngine.resolveEffectiveOccurrence(event, now)

        assertEquals(LocalDate.of(2026, 10, 21), effective.toLocalDate())
        val details = RecurrenceEngine.calculateOccurrenceDetails(event, now)
        assertFalse(details.isOverdue)
    }

    @Test
    fun explicitCompletion_oneTimeEvent_marksAsCompleted() {
        val event = CountdownEvent(
            title = "One-time Doctor Visit",
            targetDate = LocalDate.of(2026, 10, 25),
            targetTime = LocalTime.of(15, 0),
            recurrenceRule = RecurrenceRule.OneTime,
            status = EventStatus.ACTIVE
        )

        val completedInstant = Instant.parse("2026-10-25T16:00:00Z")
        val result = RecurrenceEngine.advanceToNextOccurrence(event, completedInstant)

        assertEquals(EventStatus.COMPLETED, result.status)
        assertEquals(completedInstant, result.lastCompletedAt)
    }

    @Test
    fun timeZoneHandling_preservesInstantAcrossTimeZones() {
        // Event at 15:00 in New York (EDT, UTC-4) -> 19:00 UTC
        val event = CountdownEvent(
            title = "NY Conference",
            targetDate = LocalDate.of(2026, 6, 15),
            targetTime = LocalTime.of(15, 0),
            timeZoneId = "America/New_York",
            recurrenceRule = RecurrenceRule.OneTime
        )

        val anchor = RecurrenceEngine.getAnchorZonedDateTime(event)
        val inUtc = anchor.withZoneSameInstant(utcZone)

        assertEquals(19, inUtc.hour)
        assertEquals(15, inUtc.dayOfMonth)

        // Check from Tokyo (UTC+9)
        val nowInTokyo = ZonedDateTime.of(2026, 6, 15, 23, 0, 0, 0, tokyoZone)
        val details = RecurrenceEngine.calculateOccurrenceDetails(event, nowInTokyo)
        assertFalse(details.isOverdue)
    }
}
