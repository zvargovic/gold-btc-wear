package hr.zvargovic.goldbtcwear.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "spot_store")

class SpotStore(private val context: Context) {

    private object Keys {
        val LAST_SPOT_EUR = doublePreferencesKey("last_spot_eur")
        val REF_SPOT_EUR  = doublePreferencesKey("ref_spot_eur")
    }

    // Zadnji izračunati spot (Yahoo × K)
    val lastSpotFlow: Flow<Double?> =
        context.dataStore.data
            .map { it[Keys.LAST_SPOT_EUR] }
            .distinctUntilChanged()

    // Referentni spot za točan % izračun
    val refSpotFlow: Flow<Double?> =
        context.dataStore.data
            .map { it[Keys.REF_SPOT_EUR] }
            .distinctUntilChanged()

    /** Spremi zadnji spot. */
    suspend fun saveLast(spot: Double) {
        context.dataStore.edit { it[Keys.LAST_SPOT_EUR] = spot }
    }

    /** Postavi referentni spot (ručno). */
    suspend fun setRef(value: Double) {
        context.dataStore.edit { it[Keys.REF_SPOT_EUR] = value }
    }

    /** Obriši referentni spot (nema refa → % = 0 do inicijalizacije). */
    suspend fun clearRef() {
        context.dataStore.edit { it.remove(Keys.REF_SPOT_EUR) }
    }

    /** Postavi ref na trenutačni last ako postoji (quick action / reset). */
    suspend fun setRefToCurrent() {
        val last = loadLast()
        if (last != null) setRef(last)
    }

    // === HELPERI: instant čitanje trenutnih vrijednosti iz DataStore-a (suspend) ===

    /** Vrati referentni spot (ako je valjan) ili null. */
    suspend fun loadRef(): Double? {
        return context.dataStore.data
            .map { it[Keys.REF_SPOT_EUR] }
            .firstOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }
    }

    /** Vrati zadnji spot (ako je valjan) ili null. */
    suspend fun loadLast(): Double? {
        return context.dataStore.data
            .map { it[Keys.LAST_SPOT_EUR] }
            .firstOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }
    }

    /**
     * Ako ref još nije postavljen, postavi ga na zadnji poznati spot
     * (ili na 'candidate' ako je proslijeđen). Vraća efektivni ref.
     */
    suspend fun ensureRefInitialized(candidate: Double? = null): Double? {
        val currentRef = loadRef()
        if (currentRef != null) return currentRef

        val src = candidate ?: loadLast()
        if (src != null) {
            setRef(src)
            return src
        }
        return null
    }
}

// kotlinx.coroutines.flow.firstOrNull helper (bez dodatnih ovisnosti)
private suspend fun <T> Flow<T>.firstOrNull(): T? {
    var v: T? = null
    try {
        collect { value ->
            v = value
            throw Stop() // prekid kolekcije čim dobijemo prvi element
        }
    } catch (_: Stop) {
        // no-op
    }
    return v
}
private class Stop : Throwable()