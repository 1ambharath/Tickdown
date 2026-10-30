package com.example.tickdown.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tickdown.data.preferences.AppPreferencesRepository
import com.example.tickdown.data.preferences.ThemeMode
import com.example.tickdown.domain.model.EventStatus
import com.example.tickdown.domain.model.RolloverBehavior
import com.example.tickdown.domain.repository.CountdownRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultRollover: RolloverBehavior = RolloverBehavior.AUTOMATIC_NEXT_OCCURRENCE,
    val totalCount: Int = 0,
    val activeCount: Int = 0,
    val completedCount: Int = 0
)

class SettingsViewModel(
    private val preferencesRepository: AppPreferencesRepository,
    private val countdownRepository: CountdownRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.themeModeFlow,
        preferencesRepository.defaultRolloverFlow,
        countdownRepository.observeAllEvents()
    ) { theme, rollover, events ->
        val active = events.count { it.status == EventStatus.ACTIVE }
        val completed = events.count { it.status == EventStatus.COMPLETED }
        SettingsUiState(
            themeMode = theme,
            defaultRollover = rollover,
            totalCount = events.size,
            activeCount = active,
            completedCount = completed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setDefaultRollover(behavior: RolloverBehavior) {
        viewModelScope.launch {
            preferencesRepository.setDefaultRollover(behavior)
        }
    }
}
