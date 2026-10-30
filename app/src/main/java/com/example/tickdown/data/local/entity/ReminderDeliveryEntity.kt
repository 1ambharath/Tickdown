package com.example.tickdown.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "reminder_deliveries",
    primaryKeys = ["eventId", "reminderOffsetMinutes", "occurrenceEpochSecond"]
)
data class ReminderDeliveryEntity(
    val eventId: Long,
    val reminderOffsetMinutes: Long,
    val occurrenceEpochSecond: Long,
    val deliveredAt: Long
)
