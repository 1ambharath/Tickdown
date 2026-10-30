package com.example.tickdown.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tickdown.data.local.entity.ReminderDeliveryEntity

@Dao
interface ReminderDeliveryDao {

    @Query("""
        SELECT COUNT(*) > 0 FROM reminder_deliveries 
        WHERE eventId = :eventId 
          AND reminderOffsetMinutes = :offsetMinutes 
          AND occurrenceEpochSecond = :occurrenceEpochSec
    """)
    suspend fun isDelivered(
        eventId: Long,
        offsetMinutes: Long,
        occurrenceEpochSec: Long
    ): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDelivery(delivery: ReminderDeliveryEntity)

    @Query("DELETE FROM reminder_deliveries WHERE eventId = :eventId")
    suspend fun deleteDeliveriesForEvent(eventId: Long)

    @Query("DELETE FROM reminder_deliveries WHERE deliveredAt < :olderThanTimestamp")
    suspend fun cleanOldDeliveries(olderThanTimestamp: Long)
}
