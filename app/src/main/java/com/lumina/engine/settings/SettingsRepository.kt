package com.lumina.engine.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

private const val SETTINGS_NAME = "lumina_settings"

// DataStore delegate
private val Context.settingsDataStore by preferencesDataStore(name = SETTINGS_NAME)

interface SettingsDataSource {
    val dynamicTheme: Flow<Boolean>
    suspend fun setDynamicTheme(enabled: Boolean)
}

class SettingsRepository(private val context: Context) : SettingsDataSource {

    private val dataStore = context.settingsDataStore

    override val dynamicTheme: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DYNAMIC_THEME] ?: false
    }

    override suspend fun setDynamicTheme(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[DYNAMIC_THEME] = enabled
        }
    }

    companion object {
        private val DYNAMIC_THEME = booleanPreferencesKey("dynamic_theme")
    }
}

class InMemorySettingsRepository(initialDynamicTheme: Boolean = false) : SettingsDataSource {
    private val state = MutableStateFlow(initialDynamicTheme)
    override val dynamicTheme: Flow<Boolean> = state
    override suspend fun setDynamicTheme(enabled: Boolean) {
        state.value = enabled
    }
}
