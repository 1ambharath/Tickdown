package com.example.tickdown

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.tickdown.ui.create.CreateEditScreen
import com.example.tickdown.ui.create.CreateEditViewModel
import com.example.tickdown.ui.detail.EventDetailScreen
import com.example.tickdown.ui.detail.EventDetailViewModel
import com.example.tickdown.ui.home.HomeScreen
import com.example.tickdown.ui.home.HomeViewModel
import com.example.tickdown.ui.settings.SettingsScreen
import com.example.tickdown.ui.settings.SettingsViewModel

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.NavKey

@Composable
fun MainNavigation(
    pendingDestination: NavKey? = null,
    onClearPending: () -> Unit = {}
) {
    val backStack = rememberNavBackStack(HomeNavKey)
    val context = LocalContext.current
    val app = context.applicationContext as TickdownApplication

    LaunchedEffect(pendingDestination) {
        if (pendingDestination != null) {
            backStack.add(pendingDestination)
            onClearPending()
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = Modifier.fillMaxSize(),
        entryProvider = entryProvider {
            entry<HomeNavKey> {
                val homeViewModel: HomeViewModel = viewModel {
                    HomeViewModel(app.repository)
                }
                HomeScreen(
                    viewModel = homeViewModel,
                    onAddClick = { backStack.add(CreateEditNavKey(0L)) },
                    onEventClick = { eventId -> backStack.add(EventDetailNavKey(eventId)) },
                    onSettingsClick = { backStack.add(SettingsNavKey) },
                    modifier = Modifier.safeDrawingPadding()
                )
            }

            entry<CreateEditNavKey> { key ->
                val createEditViewModel: CreateEditViewModel = viewModel(
                    key = "create_edit_${key.eventId}"
                ) {
                    CreateEditViewModel(app.repository, key.eventId)
                }
                CreateEditScreen(
                    viewModel = createEditViewModel,
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.safeDrawingPadding()
                )
            }

            entry<EventDetailNavKey> { key ->
                val detailViewModel: EventDetailViewModel = viewModel(
                    key = "detail_${key.eventId}"
                ) {
                    EventDetailViewModel(app.repository, key.eventId)
                }
                EventDetailScreen(
                    viewModel = detailViewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onEditClick = { eventId -> backStack.add(CreateEditNavKey(eventId)) },
                    modifier = Modifier.safeDrawingPadding()
                )
            }

            entry<SettingsNavKey> {
                val settingsViewModel: SettingsViewModel = viewModel {
                    SettingsViewModel(app.preferencesRepository, app.repository)
                }
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
        }
    )
}
