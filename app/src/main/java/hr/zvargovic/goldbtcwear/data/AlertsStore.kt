package hr.zvargovic.goldbtcwear.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

// DataStore instanca vezana uz Context
private val Context.alertsDataStore by preferencesDataStore(name = "alerts_prefs")

class AlertsStore(private val context: Context) {
    private val KEY_ALERTS = stringPreferencesKey("alerts_json")

    // Moshi adapter za List<Double>
    private val moshi = Moshi.Builder().build()
    private val listType = Types.newParameterizedType(List::class.java, java.lang.Double::class.java)
    private val adapter = moshi.adapter<List<Double>>(listType)

    suspend fun load(): List<Double> {
        val prefs = context.alertsDataStore.data.first()
        val json = prefs[KEY_ALERTS] ?: "[]"
        return runCatching { adapter.fromJson(json) ?: emptyList() }.getOrElse { emptyList() }
    }

    suspend fun save(list: List<Double>) {
        val json = adapter.toJson(list)
        context.alertsDataStore.edit { it[KEY_ALERTS] = json }
    }
}