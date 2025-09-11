package hr.zvargovic.goldbtcwear.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow

private val Context.dataStore by preferencesDataStore(name = "spot_store")

class SpotStore(private val context: Context) {

    private object Keys {
        val LAST_SPOT_EUR = doublePreferencesKey("last_spot_eur")
        val REF_SPOT_EUR  = doublePreferencesKey("ref_spot_eur")
    }

    // Zadnji izračunati spot (Yahoo × K)
    val lastSpotFlow: Flow<Double?> =
        context.dataStore.data.map { it[Keys.LAST_SPOT_EUR] }.distinctUntilChanged()

    // Referentni spot za točan % izračun
    val refSpotFlow: Flow<Double?> =
        context.dataStore.data.map { it[Keys.REF_SPOT_EUR] }.distinctUntilChanged()

    suspend fun saveLast(spot: Double) {
        context.dataStore.edit { it[Keys.LAST_SPOT_EUR] = spot }
    }

    suspend fun setRef(value: Double) {
        context.dataStore.edit { it[Keys.REF_SPOT_EUR] = value }
    }

    suspend fun clearRef() {
        context.dataStore.edit { it.remove(Keys.REF_SPOT_EUR) }
    }

    // Helper: postavi ref na trenutačni last ako postoji
    suspend fun setRefToCurrent() {
        val last = lastSpotFlow.firstOrNull()
        if (last != null && last.isFinite() && last > 0.0) setRef(last)
    }
}

// kotlinx.coroutines.flow.firstOrNull helper (bez dodatnih ovisnosti)
private suspend fun <T> Flow<T>.firstOrNull(): T? {
    var v: T? = null
    try {
        collect { value ->
            v = value
            throw Stop()
        }
    } catch (_: Stop) { /* break */ }
    return v
}
private class Stop : Throwable()