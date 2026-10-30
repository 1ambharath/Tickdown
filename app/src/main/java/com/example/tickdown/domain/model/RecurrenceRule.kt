package com.example.tickdown.domain.model

import java.time.DayOfWeek
import java.time.Month

sealed interface RecurrenceRule {
    data object OneTime : RecurrenceRule

    data class Daily(val interval: Int = 1) : RecurrenceRule {
        init {
            require(interval >= 1) { "Daily interval must be >= 1" }
        }
    }

    data class Weekly(
        val daysOfWeek: Set<DayOfWeek>,
        val interval: Int = 1
    ) : RecurrenceRule {
        init {
            require(daysOfWeek.isNotEmpty()) { "At least one day of the week must be selected" }
            require(interval >= 1) { "Weekly interval must be >= 1" }
        }
    }

    sealed interface Monthly : RecurrenceRule {
        /**
         * Scheduled on a fixed day of month (1..31).
         * If a month has fewer days (e.g. Feb 28/29, Apr 30), it will adjust to the last valid day
         * of that month during calculation WITHOUT mutating the original target day.
         */
        data class DayOfMonth(val day: Int) : Monthly {
            init {
                require(day in 1..31) { "Day of month must be between 1 and 31" }
            }
        }

        /**
         * Always recurs on the last calendar day of every month (28th/29th, 30th, or 31st).
         */
        data object LastDay : Monthly
    }

    /**
     * Scheduled on a specific month and day (1..31).
     * If February 29th is specified, in non-leap years it recurs on February 28th
     * without altering the original recurrence rule definition.
     */
    data class Yearly(
        val month: Month,
        val dayOfMonth: Int
    ) : RecurrenceRule {
        init {
            require(dayOfMonth in 1..31) { "Day of month must be between 1 and 31" }
        }
    }
}
