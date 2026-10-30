package com.example.tickdown.domain.model

import java.time.LocalTime

sealed interface ReminderRule {
    data object None : ReminderRule

    data object AtTimeOfEvent : ReminderRule

    data class MinutesBefore(val minutes: Int) : ReminderRule {
        init {
            require(minutes > 0) { "Minutes must be > 0" }
        }
    }

    data class HoursBefore(val hours: Int) : ReminderRule {
        init {
            require(hours > 0) { "Hours must be > 0" }
        }
    }

    data class DaysBefore(
        val days: Int,
        val atTime: LocalTime? = null
    ) : ReminderRule {
        init {
            require(days > 0) { "Days must be > 0" }
        }
    }
}
