package com.example.tickdown.domain.model

data class EventReminder(
    val minutesBefore: Long
) : Comparable<EventReminder> {

    companion object {
        val AT_DEADLINE = EventReminder(0L)
        val BEFORE_15_MIN = EventReminder(15L)
        val BEFORE_30_MIN = EventReminder(30L)
        val BEFORE_1_HOUR = EventReminder(60L)
        val BEFORE_2_HOURS = EventReminder(120L)
        val BEFORE_1_DAY = EventReminder(1440L)
        val BEFORE_2_DAYS = EventReminder(2880L)
        val BEFORE_1_WEEK = EventReminder(10080L)

        val STANDARD_PRESETS = listOf(
            AT_DEADLINE,
            BEFORE_15_MIN,
            BEFORE_1_HOUR,
            BEFORE_1_DAY,
            BEFORE_1_WEEK
        )
    }

    override fun compareTo(other: EventReminder): Int = minutesBefore.compareTo(other.minutesBefore)

    val label: String
        get() = when (minutesBefore) {
            0L -> "At deadline"
            15L -> "15 minutes before"
            30L -> "30 minutes before"
            60L -> "1 hour before"
            120L -> "2 hours before"
            1440L -> "1 day before"
            2880L -> "2 days before"
            10080L -> "1 week before"
            else -> if (minutesBefore < 60) {
                "$minutesBefore minutes before"
            } else if (minutesBefore < 1440) {
                "${minutesBefore / 60} hours before"
            } else {
                "${minutesBefore / 1440} days before"
            }
        }
}
