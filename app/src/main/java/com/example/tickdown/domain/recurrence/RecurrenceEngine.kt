package com.example.tickdown.domain.recurrence

import com.example.tickdown.domain.model.CountdownEvent
import com.example.tickdown.domain.model.EventStatus
import com.example.tickdown.domain.model.RecurrenceRule
import com.example.tickdown.domain.model.RolloverBehavior
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class OccurrenceDetails(
    val eventId: Long,
    val occurrenceDateTime: ZonedDateTime,
    val isOverdue: Boolean,
    val isToday: Boolean,
    val isDateOnly: Boolean,
    val daysRemaining: Long,
    val hoursRemaining: Long,
    val minutesRemaining: Long,
    val secondsRemaining: Long,
    val totalSecondsDifference: Long
)

object RecurrenceEngine {

    /**
     * Resolves the anchor ZonedDateTime of the event.
     */
    fun getAnchorZonedDateTime(event: CountdownEvent): ZonedDateTime {
        val zone = event.resolvedZoneId
        val time = event.targetTime ?: LocalTime.MIDNIGHT
        return ZonedDateTime.of(event.targetDate, time, zone)
    }

    /**
     * Combines a local date and optional local time into a ZonedDateTime.
     */
    fun toZonedDateTime(date: LocalDate, time: LocalTime?, zoneId: ZoneId): ZonedDateTime {
        return ZonedDateTime.of(date, time ?: LocalTime.MIDNIGHT, zoneId)
    }

    /**
     * Returns true if the occurrence is considered past relative to [referenceTime].
     * For date-only events, the occurrence is considered past only after the calendar day has ended
     * (i.e. occurrence date is strictly before reference date).
     * For timed events, it is past when the occurrence instant is strictly <= reference instant.
     */
    fun isPast(occurrence: ZonedDateTime, isDateOnly: Boolean, referenceTime: ZonedDateTime): Boolean {
        val refInSameZone = referenceTime.withZoneSameInstant(occurrence.zone)
        return if (isDateOnly) {
            occurrence.toLocalDate().isBefore(refInSameZone.toLocalDate())
        } else {
            !occurrence.isAfter(refInSameZone)
        }
    }

    /**
     * Calculates the effective occurrence for display and countdown.
     * Takes into account rollover behavior and event status.
     *
     * - If paused or completed, returns the anchor/last occurrence.
     * - If [RolloverBehavior.REQUIRE_MANUAL_COMPLETION], preserves the current occurrence even if overdue
     *   until the user explicitly completes it.
     * - If [RolloverBehavior.AUTOMATIC_NEXT_OCCURRENCE], automatically rolls over to the next
     *   upcoming occurrence once passed.
     */
    fun resolveEffectiveOccurrence(
        event: CountdownEvent,
        referenceTime: ZonedDateTime
    ): ZonedDateTime {
        val zone = event.resolvedZoneId
        val refInSameZone = referenceTime.withZoneSameInstant(zone)
        val anchorZdt = toZonedDateTime(event.targetDate, event.targetTime, zone)

        if (event.status != EventStatus.ACTIVE) {
            return anchorZdt
        }

        val anchorIsPast = isPast(anchorZdt, !event.isTimed, refInSameZone)

        return when (event.recurrenceRule) {
            is RecurrenceRule.OneTime -> anchorZdt

            else -> {
                if (!anchorIsPast) {
                    anchorZdt
                } else {
                    when (event.rolloverBehavior) {
                        RolloverBehavior.REQUIRE_MANUAL_COMPLETION -> anchorZdt
                        RolloverBehavior.AUTOMATIC_NEXT_OCCURRENCE -> {
                            calculateNextOccurrence(event, refInSameZone) ?: anchorZdt
                        }
                    }
                }
            }
        }
    }

    /**
     * Calculates the earliest occurrence that is on or after [fromTime] (not past).
     * Returns null for OneTime events that are already past.
     */
    fun calculateNextOccurrence(
        event: CountdownEvent,
        fromTime: ZonedDateTime
    ): ZonedDateTime? {
        val zone = event.resolvedZoneId
        val from = fromTime.withZoneSameInstant(zone)
        val initialZdt = toZonedDateTime(event.targetDate, event.targetTime, zone)
        val isDateOnly = !event.isTimed

        if (!isPast(initialZdt, isDateOnly, from)) {
            return initialZdt
        }

        return when (val rule = event.recurrenceRule) {
            is RecurrenceRule.OneTime -> null

            is RecurrenceRule.Daily -> {
                calculateNextDaily(event.targetDate, event.targetTime, zone, rule.interval, from, isDateOnly)
            }

            is RecurrenceRule.Weekly -> {
                calculateNextWeekly(event.targetDate, event.targetTime, zone, rule.daysOfWeek, rule.interval, from, isDateOnly)
            }

            is RecurrenceRule.Monthly.DayOfMonth -> {
                calculateNextMonthlyDay(event.targetDate, event.targetTime, zone, rule.day, from, isDateOnly)
            }

            is RecurrenceRule.Monthly.LastDay -> {
                calculateNextMonthlyLastDay(event.targetDate, event.targetTime, zone, from, isDateOnly)
            }

            is RecurrenceRule.Yearly -> {
                calculateNextYearly(event.targetDate, event.targetTime, zone, rule.month, rule.dayOfMonth, from, isDateOnly)
            }
        }
    }

    /**
     * Calculates the strictly next occurrence after the given [referenceTime].
     * Useful when advancing/completing an occurrence.
     */
    fun calculateStrictlyNextOccurrence(
        event: CountdownEvent,
        referenceTime: ZonedDateTime
    ): ZonedDateTime? {
        val zone = event.resolvedZoneId
        val from = referenceTime.withZoneSameInstant(zone)
        val nextSearchStart = if (event.isTimed) {
            from.plusNanos(1)
        } else {
            from.plusDays(1).with(LocalTime.MIN)
        }

        return when (val rule = event.recurrenceRule) {
            is RecurrenceRule.OneTime -> null

            is RecurrenceRule.Daily -> {
                calculateNextDaily(event.targetDate, event.targetTime, zone, rule.interval, nextSearchStart, !event.isTimed)
            }

            is RecurrenceRule.Weekly -> {
                calculateNextWeekly(event.targetDate, event.targetTime, zone, rule.daysOfWeek, rule.interval, nextSearchStart, !event.isTimed)
            }

            is RecurrenceRule.Monthly.DayOfMonth -> {
                calculateNextMonthlyDay(event.targetDate, event.targetTime, zone, rule.day, nextSearchStart, !event.isTimed)
            }

            is RecurrenceRule.Monthly.LastDay -> {
                calculateNextMonthlyLastDay(event.targetDate, event.targetTime, zone, nextSearchStart, !event.isTimed)
            }

            is RecurrenceRule.Yearly -> {
                calculateNextYearly(event.targetDate, event.targetTime, zone, rule.month, rule.dayOfMonth, nextSearchStart, !event.isTimed)
            }
        }
    }

    /**
     * Advances an event upon explicit user completion.
     * - For OneTime events, marks status as COMPLETED.
     * - For recurring events, updates targetDate to next occurrence, updates lastCompletedAt, and preserves rule.
     */
    fun advanceToNextOccurrence(
        event: CountdownEvent,
        completionInstant: Instant = Instant.now()
    ): CountdownEvent {
        if (event.recurrenceRule is RecurrenceRule.OneTime) {
            return event.copy(
                status = EventStatus.COMPLETED,
                lastCompletedAt = completionInstant,
                updatedAt = completionInstant
            )
        }

        val currentOccurrence = toZonedDateTime(event.targetDate, event.targetTime, event.resolvedZoneId)
        val completionZdt = completionInstant.atZone(event.resolvedZoneId)
        // Advance strictly after whichever is later: current occurrence or completion time
        val referenceZdt = if (completionZdt.isAfter(currentOccurrence)) completionZdt else currentOccurrence
        val nextOccurrence = calculateStrictlyNextOccurrence(event, referenceZdt)

        return if (nextOccurrence != null) {
            event.copy(
                targetDate = nextOccurrence.toLocalDate(),
                lastCompletedAt = completionInstant,
                status = EventStatus.ACTIVE,
                updatedAt = completionInstant
            )
        } else {
            event.copy(
                status = EventStatus.COMPLETED,
                lastCompletedAt = completionInstant,
                updatedAt = completionInstant
            )
        }
    }

    /**
     * Computes the exact countdown details for UI display.
     */
    fun calculateOccurrenceDetails(
        event: CountdownEvent,
        now: ZonedDateTime
    ): OccurrenceDetails {
        val zone = event.resolvedZoneId
        val nowInZone = now.withZoneSameInstant(zone)
        val occurrenceZdt = resolveEffectiveOccurrence(event, nowInZone)
        val isDateOnly = !event.isTimed

        val isOverdue = isPast(occurrenceZdt, isDateOnly, nowInZone)
        val isToday = isDateOnly && occurrenceZdt.toLocalDate() == nowInZone.toLocalDate()

        val diffDuration = if (isDateOnly) {
            val targetStartOfDay = occurrenceZdt.toLocalDate().atStartOfDay(zone)
            val nowStartOfDay = nowInZone.toLocalDate().atStartOfDay(zone)
            Duration.between(nowStartOfDay, targetStartOfDay)
        } else {
            Duration.between(nowInZone, occurrenceZdt)
        }

        val totalSeconds = diffDuration.seconds
        val absTotalSeconds = Math.abs(totalSeconds)

        val days = absTotalSeconds / 86400
        val hours = (absTotalSeconds % 86400) / 3600
        val minutes = (absTotalSeconds % 3600) / 60
        val seconds = absTotalSeconds % 60

        return OccurrenceDetails(
            eventId = event.id,
            occurrenceDateTime = occurrenceZdt,
            isOverdue = isOverdue,
            isToday = isToday,
            isDateOnly = isDateOnly,
            daysRemaining = days,
            hoursRemaining = hours,
            minutesRemaining = minutes,
            secondsRemaining = seconds,
            totalSecondsDifference = totalSeconds
        )
    }

    // --- Private Calculation Helpers ---

    private fun calculateNextDaily(
        startDate: LocalDate,
        time: LocalTime?,
        zone: ZoneId,
        interval: Int,
        from: ZonedDateTime,
        isDateOnly: Boolean
    ): ZonedDateTime {
        val fromDate = from.toLocalDate()
        val daysBetween = ChronoUnit.DAYS.between(startDate, fromDate)

        val multiplier = if (daysBetween > 0) {
            (daysBetween / interval) * interval
        } else {
            0
        }

        var candidateDate = startDate.plusDays(multiplier)
        while (isPast(toZonedDateTime(candidateDate, time, zone), isDateOnly, from)) {
            candidateDate = candidateDate.plusDays(interval.toLong())
        }

        return toZonedDateTime(candidateDate, time, zone)
    }

    private fun calculateNextWeekly(
        startDate: LocalDate,
        time: LocalTime?,
        zone: ZoneId,
        daysOfWeek: Set<DayOfWeek>,
        interval: Int,
        from: ZonedDateTime,
        isDateOnly: Boolean
    ): ZonedDateTime {
        // Anchor to the Monday of the start date week
        val anchorMonday = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sortedDays = daysOfWeek.sorted()

        var weekOffset = 0L
        val maxWeeks = 520L // Bounded to 10 years
        while (weekOffset < maxWeeks) {
            val currentMonday = anchorMonday.plusWeeks(weekOffset * interval)

            for (dow in sortedDays) {
                val candidateDate = currentMonday.with(TemporalAdjusters.nextOrSame(dow))
                if (candidateDate < startDate) {
                    continue
                }
                val candidateZdt = toZonedDateTime(candidateDate, time, zone)
                if (!isPast(candidateZdt, isDateOnly, from)) {
                    return candidateZdt
                }
            }
            weekOffset++
        }

        // Fallback
        return toZonedDateTime(startDate, time, zone)
    }

    private fun calculateNextMonthlyDay(
        startDate: LocalDate,
        time: LocalTime?,
        zone: ZoneId,
        originalDay: Int,
        from: ZonedDateTime,
        isDateOnly: Boolean
    ): ZonedDateTime {
        var startYm = YearMonth.from(startDate)
        val fromYm = YearMonth.from(from.toLocalDate())

        if (fromYm.isAfter(startYm)) {
            startYm = fromYm
        }

        var ym = startYm
        val maxMonths = 240 // 20 years search window
        var count = 0

        while (count < maxMonths) {
            val maxDaysInMonth = ym.lengthOfMonth()
            // Clamps to last valid day of month (e.g., Feb 28/29, Apr 30) without mutating originalDay
            val validDay = minOf(originalDay, maxDaysInMonth)
            val candidateDate = ym.atDay(validDay)

            if (candidateDate >= startDate) {
                val candidateZdt = toZonedDateTime(candidateDate, time, zone)
                if (!isPast(candidateZdt, isDateOnly, from)) {
                    return candidateZdt
                }
            }

            ym = ym.plusMonths(1)
            count++
        }

        return toZonedDateTime(startDate, time, zone)
    }

    private fun calculateNextMonthlyLastDay(
        startDate: LocalDate,
        time: LocalTime?,
        zone: ZoneId,
        from: ZonedDateTime,
        isDateOnly: Boolean
    ): ZonedDateTime {
        var ym = YearMonth.from(startDate)
        val fromYm = YearMonth.from(from.toLocalDate())

        if (fromYm.isAfter(ym)) {
            ym = fromYm
        }

        var count = 0
        val maxMonths = 240

        while (count < maxMonths) {
            val candidateDate = ym.atEndOfMonth()
            if (candidateDate >= startDate) {
                val candidateZdt = toZonedDateTime(candidateDate, time, zone)
                if (!isPast(candidateZdt, isDateOnly, from)) {
                    return candidateZdt
                }
            }
            ym = ym.plusMonths(1)
            count++
        }

        return toZonedDateTime(startDate, time, zone)
    }

    private fun calculateNextYearly(
        startDate: LocalDate,
        time: LocalTime?,
        zone: ZoneId,
        month: java.time.Month,
        originalDayOfMonth: Int,
        from: ZonedDateTime,
        isDateOnly: Boolean
    ): ZonedDateTime {
        var year = maxOf(startDate.year, from.year)
        val maxYears = 50
        var count = 0

        while (count < maxYears) {
            val isLeap = Year.isLeap(year.toLong())
            val maxDay = month.length(isLeap)
            // Handles leap year Feb 29 -> Feb 28 on non-leap years seamlessly
            val validDay = minOf(originalDayOfMonth, maxDay)
            val candidateDate = LocalDate.of(year, month, validDay)

            if (candidateDate >= startDate) {
                val candidateZdt = toZonedDateTime(candidateDate, time, zone)
                if (!isPast(candidateZdt, isDateOnly, from)) {
                    return candidateZdt
                }
            }

            year++
            count++
        }

        return toZonedDateTime(startDate, time, zone)
    }
}
