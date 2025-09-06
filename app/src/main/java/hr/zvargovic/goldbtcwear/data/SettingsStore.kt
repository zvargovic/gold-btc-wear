package hr.zvargovic.goldbtcwear.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings_prefs")

class SettingsStore(private val context: Context) {

    companion object {
        private val KEY_API = stringPreferencesKey("api_key")
        private val KEY_ALARM = booleanPreferencesKey("alarm_enabled")
    }

    val apiKeyFlow: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_API] ?: "" }

    val alarmEnabledFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_ALARM] ?: false }

    suspend fun loadApiKey(): String =
        context.settingsDataStore.data.first()[KEY_API] ?: ""

    suspend fun loadAlarmEnabled(): Boolean =
        context.settingsDataStore.data.first()[KEY_ALARM] ?: false

    suspend fun saveApiKey(apiKey: String) {
        context.settingsDataStore.edit { it[KEY_API] = apiKey }
    }

    suspend fun saveAlarmEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_ALARM] = enabled }
    }

    suspend fun saveAll(apiKey: String, alarmEnabled: Boolean) {
        context.settingsDataStore.edit {
            it[KEY_API] = apiKey
            it[KEY_ALARM] = alarmEnabled
        }
    }
}