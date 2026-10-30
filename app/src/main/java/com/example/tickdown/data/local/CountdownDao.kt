package com.example.tickdown.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.tickdown.data.local.entity.CountdownEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CountdownDao {

    @Query("SELECT * FROM countdown_events ORDER BY createdAt DESC")
    fun getAllEventsFlow(): Flow<List<CountdownEventEntity>>

    @Query("SELECT * FROM countdown_events WHERE id = :id LIMIT 1")
    fun getEventByIdFlow(id: Long): Flow<CountdownEventEntity?>

    @Query("SELECT * FROM countdown_events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: Long): CountdownEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(entity: CountdownEventEntity): Long

    @Update
    suspend fun updateEvent(entity: CountdownEventEntity)

    @Query("DELETE FROM countdown_events WHERE id = :id")
    suspend fun deleteEventById(id: Long)

    @Query("UPDATE countdown_events SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long)
}
