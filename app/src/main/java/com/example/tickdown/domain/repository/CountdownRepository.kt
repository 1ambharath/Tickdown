package com.example.tickdown.domain.repository

import com.example.tickdown.domain.model.CountdownEvent
import kotlinx.coroutines.flow.Flow

interface CountdownRepository {
    fun observeAllEvents(): Flow<List<CountdownEvent>>

    fun observeEventById(id: Long): Flow<CountdownEvent?>

    suspend fun getEventById(id: Long): CountdownEvent?

    suspend fun insertEvent(event: CountdownEvent): Long

    suspend fun updateEvent(event: CountdownEvent)

    suspend fun deleteEvent(id: Long)

    suspend fun pauseEvent(id: Long)

    suspend fun resumeEvent(id: Long)

    suspend fun completeEvent(id: Long)

    suspend fun duplicateEvent(id: Long): Long
}
