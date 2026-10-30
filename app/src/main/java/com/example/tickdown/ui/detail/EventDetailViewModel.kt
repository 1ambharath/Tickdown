package com.example.tickdown.ui.detail

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

data class EventDetailUiState(
    val event: CountdownEvent? = null,
    val details: OccurrenceDetails? = null,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
    val duplicatedEventId: Long? = null
)

class EventDetailViewModel(
    private val repository: CountdownRepository,
    private val eventId: Long
) : ViewModel() {

    private val deletedFlow = MutableStateFlow(false)
    private val duplicatedFlow = MutableStateFlow<Long?>(null)

    private val tickerFlow = flow {
        while (true) {
            emit(ZonedDateTime.now())
            delay(1000)
        }
    }

    val uiState: StateFlow<EventDetailUiState> = combine(
        repository.observeEventById(eventId),
        tickerFlow,
        deletedFlow,
        duplicatedFlow
    ) { event, now, isDeleted, dupId ->
        if (event == null) {
            EventDetailUiState(isLoading = false, isDeleted = isDeleted)
        } else {
            val details = RecurrenceEngine.calculateOccurrenceDetails(event, now)
            EventDetailUiState(
                event = event,
                details = details,
                isLoading = false,
                isDeleted = isDeleted,
                duplicatedEventId = dupId
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EventDetailUiState(isLoading = true)
    )

    fun completeEvent() {
        viewModelScope.launch {
            repository.completeEvent(eventId)
        }
    }

    fun togglePause() {
        val currentEvent = uiState.value.event ?: return
        viewModelScope.launch {
            if (currentEvent.status == EventStatus.PAUSED) {
                repository.resumeEvent(eventId)
            } else {
                repository.pauseEvent(eventId)
            }
        }
    }

    fun duplicateEvent() {
        viewModelScope.launch {
            val newId = repository.duplicateEvent(eventId)
            if (newId > 0) {
                duplicatedFlow.value = newId
            }
        }
    }

    fun deleteEvent() {
        viewModelScope.launch {
            repository.deleteEvent(eventId)
            deletedFlow.value = true
        }
    }
}
