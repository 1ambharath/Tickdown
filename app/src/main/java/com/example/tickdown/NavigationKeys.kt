package com.example.tickdown

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeNavKey : NavKey

@Serializable
data class CreateEditNavKey(val eventId: Long = 0L) : NavKey

@Serializable
data class EventDetailNavKey(val eventId: Long) : NavKey

@Serializable
data object SettingsNavKey : NavKey
