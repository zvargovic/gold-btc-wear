package hr.zvargovic.goldbtcwear.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

private val Context.dataStore by preferencesDataStore(name = "rsi_store")

/**
 * Jednostavan spremnik recentnih closeova.
 * Serijalizira listu kao CSV (npr. "2312.5,2313.1,...").
 */
class RsiStore(private val context: Context) {

    private object Keys {
        val CLOSES_CSV = stringPreferencesKey("rsi_closes_csv")
    }

    /** Učitaj sve closeove (najstariji -> najnoviji). */
    suspend fun loadAll(): List<Double> {
        val csv = context.dataStore.data
            .map { it[Keys.CLOSES_CSV] ?: "" }
            .firstOrNullRsi()
            .orEmpty()

        return csv.split(',')
            .mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() }?.toDoubleOrNull() }
    }

    /** Spremi novu listu (truncate na maxItems radi veličine). */
    suspend fun saveAll(closes: List<Double>, maxItems: Int = 200) {
        val trimmed = if (closes.size > maxItems) closes.takeLast(maxItems) else closes
        val csv = trimmed.joinToString(separator = ",")
        context.dataStore.edit { it[Keys.CLOSES_CSV] = csv }
    }

    /** Dodaj jedan close i spremi (prerezano na maxItems). */
    suspend fun append(close: Double, maxItems: Int = 200) {
        val current = loadAll().toMutableList()
        current += close
        saveAll(current, maxItems)
    }
}

/* ----------------- Lokalni helperi (bez kolizije sa SpotStore) ----------------- */

// flow first-or-null, lokalno ime da ne sudara s drugim fileovima
private suspend fun <T> Flow<T>.firstOrNullRsi(): T? {
    var v: T? = null
    try {
        collect { value ->
            v = value
            throw RsiStop()
        }
    } catch (_: RsiStop) { /* prekid */ }
    return v
}

// posebna “break” iznimka samo za ovaj file
private class RsiStop : Throwable()