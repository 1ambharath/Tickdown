package com.example.tickdown.domain.model

enum class RolloverBehavior {
    /**
     * When the scheduled occurrence passes, automatically calculate and count down
     * to the next upcoming recurrence cycle without user intervention.
     */
    AUTOMATIC_NEXT_OCCURRENCE,

    /**
     * When the scheduled occurrence passes, remain anchored at that occurrence in an overdue
     * state until the user explicitly triggers completion to roll over to the next cycle.
     */
    REQUIRE_MANUAL_COMPLETION
}
