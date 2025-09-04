package hr.zvargovic.goldbtcwear.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.selectedAlertDataStore by preferencesDataStore(name = "selected_alert_store")

class SelectedAlertStore(private val context: Context) {
    companion object {
        private val KEY_SELECTED = stringPreferencesKey("selected_alert")
    }

    suspend fun save(value: Double?) {
        context.selectedAlertDataStore.edit { prefs ->
            if (value == null) {
                prefs.remove(KEY_SELECTED)
            } else {
                prefs[KEY_SELECTED] = value.toString()
            }
        }
    }

    suspend fun load(): Double? =
        context.selectedAlertDataStore.data
            .map { it[KEY_SELECTED] }
            .first()
            ?.toDoubleOrNull()
}