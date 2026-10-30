package com.example.tickdown.ui.home

import com.example.tickdown.domain.model.CountdownEvent
import com.example.tickdown.domain.model.EventStatus
import com.example.tickdown.domain.model.RecurrenceRule
import com.example.tickdown.domain.repository.CountdownRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.LocalDate

class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_loadsAndSortsEventsByNearestOccurrence() = runTest {
        val fakeRepo = FakeCountdownRepository()
        val today = LocalDate.now()

        val eventFar = CountdownEvent(
            id = 1,
            title = "Far Event",
            targetDate = today.plusDays(10),
            recurrenceRule = RecurrenceRule.OneTime
        )
        val eventNear = CountdownEvent(
            id = 2,
            title = "Near Event",
            targetDate = today.plusDays(2),
            recurrenceRule = RecurrenceRule.OneTime
        )

        fakeRepo.eventsFlow.value = listOf(eventFar, eventNear)

        val viewModel = HomeViewModel(fakeRepo)
        val state = viewModel.uiState.first { !it.isLoading }

        assertFalse(state.isLoading)
        assertEquals(2, state.events.size)
        // Near event should appear first
        assertEquals(2L, state.events[0].event.id)
        assertEquals(1L, state.events[1].event.id)
    }

    @Test
    fun filter_filtersEventsByStatus() = runTest {
        val fakeRepo = FakeCountdownRepository()
        val eventActive = CountdownEvent(id = 1, title = "Active", targetDate = LocalDate.now(), status = EventStatus.ACTIVE)
        val eventPaused = CountdownEvent(id = 2, title = "Paused", targetDate = LocalDate.now(), status = EventStatus.PAUSED)

        fakeRepo.eventsFlow.value = listOf(eventActive, eventPaused)

        val viewModel = HomeViewModel(fakeRepo)
        viewModel.setFilter(EventStatus.PAUSED)

        val state = viewModel.uiState.first { it.filterStatus == EventStatus.PAUSED }
        assertEquals(1, state.events.size)
        assertEquals(EventStatus.PAUSED, state.events[0].event.status)
    }

    @Test
    fun completeEvent_advancesInRepository() = runTest {
        val fakeRepo = FakeCountdownRepository()
        val event = CountdownEvent(id = 5, title = "To Complete", targetDate = LocalDate.now())
        fakeRepo.eventsFlow.value = listOf(event)

        val viewModel = HomeViewModel(fakeRepo)
        viewModel.completeEvent(5)
        testScheduler.advanceUntilIdle()

        assertTrue(fakeRepo.completedEventIds.contains(5L))
    }

    private class FakeCountdownRepository : CountdownRepository {
        val eventsFlow = MutableStateFlow<List<CountdownEvent>>(emptyList())
        val completedEventIds = mutableListOf<Long>()

        override fun observeAllEvents(): Flow<List<CountdownEvent>> = eventsFlow

        override fun observeEventById(id: Long): Flow<CountdownEvent?> {
            TODO("Not needed for HomeViewModelTest")
        }

        override suspend fun getEventById(id: Long): CountdownEvent? = eventsFlow.value.find { it.id == id }

        override suspend fun insertEvent(event: CountdownEvent): Long {
            eventsFlow.value = eventsFlow.value + event
            return event.id
        }

        override suspend fun updateEvent(event: CountdownEvent) {
            eventsFlow.value = eventsFlow.value.map { if (it.id == event.id) event else it }
        }

        override suspend fun deleteEvent(id: Long) {
            eventsFlow.value = eventsFlow.value.filter { it.id != id }
        }

        override suspend fun pauseEvent(id: Long) {
            eventsFlow.value = eventsFlow.value.map { if (it.id == id) it.copy(status = EventStatus.PAUSED) else it }
        }

        override suspend fun resumeEvent(id: Long) {
            eventsFlow.value = eventsFlow.value.map { if (it.id == id) it.copy(status = EventStatus.ACTIVE) else it }
        }

        override suspend fun completeEvent(id: Long) {
            completedEventIds.add(id)
        }

        override suspend fun duplicateEvent(id: Long): Long {
            val original = eventsFlow.value.find { it.id == id } ?: return -1L
            val dup = original.copy(id = 999L, title = "${original.title} (Copy)")
            eventsFlow.value = eventsFlow.value + dup
            return 999L
        }
    }
}
