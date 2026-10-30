package com.example.tickdown.data.repository

import android.content.Context
import com.example.tickdown.data.local.CountdownDao
import com.example.tickdown.data.local.entity.toDomain
import com.example.tickdown.data.local.entity.toEntity
import com.example.tickdown.domain.model.CountdownEvent
import com.example.tickdown.domain.model.EventStatus
import com.example.tickdown.domain.recurrence.RecurrenceEngine
import com.example.tickdown.domain.repository.CountdownRepository
import com.example.tickdown.system.scheduler.ReminderScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant

class CountdownRepositoryImpl(
    private val dao: CountdownDao,
    private val context: Context? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onDataChanged: (() -> Unit)? = null
) : CountdownRepository {

    override fun observeAllEvents(): Flow<List<CountdownEvent>> {
        return dao.getAllEventsFlow()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override fun observeEventById(id: Long): Flow<CountdownEvent?> {
        return dao.getEventByIdFlow(id)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)
    }

    override suspend fun getEventById(id: Long): CountdownEvent? = withContext(ioDispatcher) {
        dao.getEventById(id)?.toDomain()
    }

    override suspend fun insertEvent(event: CountdownEvent): Long = withContext(ioDispatcher) {
        val now = Instant.now()
        val toSave = event.copy(
            createdAt = if (event.createdAt == Instant.EPOCH) now else event.createdAt,
            updatedAt = now
        )
        val id = dao.insertEvent(toSave.toEntity())
        val saved = toSave.copy(id = id)
        context?.let { ctx ->
            ReminderScheduler.scheduleRemindersForEvent(ctx, saved)
            com.example.tickdown.widget.TickdownWidgetUpdater.updateAllWidgetsAsync(ctx)
        }
        onDataChanged?.invoke()
        id
    }

    override suspend fun updateEvent(event: CountdownEvent) {
        withContext(ioDispatcher) {
            val toSave = event.copy(updatedAt = Instant.now())
            dao.updateEvent(toSave.toEntity())
            context?.let { ctx ->
                ReminderScheduler.scheduleRemindersForEvent(ctx, toSave)
                com.example.tickdown.widget.TickdownWidgetUpdater.updateAllWidgetsAsync(ctx)
            }
            onDataChanged?.invoke()
        }
    }

    override suspend fun deleteEvent(id: Long) {
        withContext(ioDispatcher) {
            val existing = dao.getEventById(id)?.toDomain()
            if (existing != null && context != null) {
                ReminderScheduler.cancelRemindersForEvent(context, existing)
            }
            dao.deleteEventById(id)
            context?.let { ctx ->
                com.example.tickdown.widget.TickdownWidgetUpdater.updateAllWidgetsAsync(ctx)
            }
            onDataChanged?.invoke()
        }
    }

    override suspend fun pauseEvent(id: Long) {
        withContext(ioDispatcher) {
            val existing = dao.getEventById(id)?.toDomain()
            if (existing != null && context != null) {
                ReminderScheduler.cancelRemindersForEvent(context, existing)
            }
            dao.updateStatus(id, EventStatus.PAUSED.name, Instant.now().toEpochMilli())
            context?.let { ctx ->
                com.example.tickdown.widget.TickdownWidgetUpdater.updateAllWidgetsAsync(ctx)
            }
            onDataChanged?.invoke()
        }
    }

    override suspend fun resumeEvent(id: Long) {
        withContext(ioDispatcher) {
            dao.updateStatus(id, EventStatus.ACTIVE.name, Instant.now().toEpochMilli())
            val resumed = dao.getEventById(id)?.toDomain()
            if (resumed != null && context != null) {
                ReminderScheduler.scheduleRemindersForEvent(context, resumed)
            }
            context?.let { ctx ->
                com.example.tickdown.widget.TickdownWidgetUpdater.updateAllWidgetsAsync(ctx)
            }
            onDataChanged?.invoke()
        }
    }

    override suspend fun completeEvent(id: Long) {
        withContext(ioDispatcher) {
            val event = dao.getEventById(id)?.toDomain() ?: return@withContext
            context?.let { ctx ->
                ReminderScheduler.cancelRemindersForEvent(ctx, event)
            }
            val updated = RecurrenceEngine.advanceToNextOccurrence(event, Instant.now())
            dao.updateEvent(updated.toEntity())
            if (updated.status == EventStatus.ACTIVE && context != null) {
                ReminderScheduler.scheduleRemindersForEvent(context, updated)
            }
            context?.let { ctx ->
                com.example.tickdown.widget.TickdownWidgetUpdater.updateAllWidgetsAsync(ctx)
            }
            onDataChanged?.invoke()
        }
    }

    override suspend fun duplicateEvent(id: Long): Long = withContext(ioDispatcher) {
        val event = dao.getEventById(id)?.toDomain() ?: return@withContext -1L
        val now = Instant.now()
        val duplicate = event.copy(
            id = 0,
            title = "${event.title} (Copy)",
            status = EventStatus.ACTIVE,
            lastCompletedAt = null,
            createdAt = now,
            updatedAt = now
        )
        val newId = dao.insertEvent(duplicate.toEntity())
        val saved = duplicate.copy(id = newId)
        context?.let { ctx ->
            ReminderScheduler.scheduleRemindersForEvent(ctx, saved)
            com.example.tickdown.widget.TickdownWidgetUpdater.updateAllWidgetsAsync(ctx)
        }
        onDataChanged?.invoke()
        newId
    }
}
