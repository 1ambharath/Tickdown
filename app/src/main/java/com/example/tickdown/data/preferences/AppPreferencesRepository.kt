package com.example.tickdown.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.tickdown.domain.model.RolloverBehavior
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tickdown_settings")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class AppPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_ROLLOVER = stringPreferencesKey("default_rollover")
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data
        .map { prefs ->
            val value = prefs[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
            try {
                ThemeMode.valueOf(value)
            } catch (_: Exception) {
                ThemeMode.SYSTEM
            }
        }

    val defaultRolloverFlow: Flow<RolloverBehavior> = context.dataStore.data
        .map { prefs ->
            val value = prefs[PreferencesKeys.DEFAULT_ROLLOVER] ?: RolloverBehavior.AUTOMATIC_NEXT_OCCURRENCE.name
            try {
                RolloverBehavior.valueOf(value)
            } catch (_: Exception) {
                RolloverBehavior.AUTOMATIC_NEXT_OCCURRENCE
            }
        }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun setDefaultRollover(behavior: RolloverBehavior) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DEFAULT_ROLLOVER] = behavior.name
        }
    }
}
