package com.example.tickdown.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tickdown.domain.model.CountdownEvent
import com.example.tickdown.domain.model.EventStatus
import com.example.tickdown.domain.recurrence.OccurrenceDetails
import com.example.tickdown.domain.recurrence.RecurrenceEngine
import com.example.tickdown.domain.repository.CountdownRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

data class EventItemUiModel(
    val event: CountdownEvent,
    val details: OccurrenceDetails
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val events: List<EventItemUiModel> = emptyList(),
    val totalCount: Int = 0,
    val activeCount: Int = 0,
    val filterStatus: EventStatus? = null
)

class HomeViewModel(
    private val repository: CountdownRepository
) : ViewModel() {

    private val filterFlow = MutableStateFlow<EventStatus?>(null)

    // Emits ticker ticks every second to update live countdowns
    private val tickerFlow = flow {
        while (true) {
            emit(ZonedDateTime.now())
            delay(1000)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeAllEvents(),
        filterFlow,
        tickerFlow
    ) { allEvents, filter, now ->
        val filtered = if (filter != null) {
            allEvents.filter { it.status == filter }
        } else {
            allEvents
        }

        // Map events to their live occurrence details and sort by nearest upcoming occurrence
        val itemModels = filtered.map { event ->
            val details = RecurrenceEngine.calculateOccurrenceDetails(event, now)
            EventItemUiModel(event = event, details = details)
        }.sortedWith(
            compareBy(
                // Put active/overdue first, paused/completed later
                { it.event.status == EventStatus.COMPLETED },
                { it.event.status == EventStatus.PAUSED },
                // Then sort by nearest upcoming deadline
                { it.details.occurrenceDateTime.toInstant() }
            )
        )

        val activeCount = allEvents.count { it.status == EventStatus.ACTIVE }

        HomeUiState(
            isLoading = false,
            events = itemModels,
            totalCount = allEvents.size,
            activeCount = activeCount,
            filterStatus = filter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    fun setFilter(status: EventStatus?) {
        filterFlow.value = status
    }

    fun completeEvent(id: Long) {
        viewModelScope.launch {
            repository.completeEvent(id)
        }
    }

    fun togglePauseEvent(event: CountdownEvent) {
        viewModelScope.launch {
            if (event.status == EventStatus.PAUSED) {
                repository.resumeEvent(event.id)
            } else {
                repository.pauseEvent(event.id)
            }
        }
    }

    fun duplicateEvent(id: Long) {
        viewModelScope.launch {
            repository.duplicateEvent(id)
        }
    }

    fun deleteEvent(id: Long) {
        viewModelScope.launch {
            repository.deleteEvent(id)
        }
    }
}
