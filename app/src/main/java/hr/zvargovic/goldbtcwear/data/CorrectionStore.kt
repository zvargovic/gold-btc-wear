package hr.zvargovic.goldbtcwear.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.corrDataStore by preferencesDataStore("corr_prefs")

class CorrectionStore(private val ctx: Context) {
    companion object {
        private val KEY_CORR = doublePreferencesKey("corr_percent") // npr. +0.0123 = +1.23%
        private val KEY_AGE  = longPreferencesKey("corr_updated_epoch_ms")
    }

    val corrFlow: Flow<Double> = ctx.corrDataStore.data.map { it[KEY_CORR] ?: 0.0 }
    val ageFlow:  Flow<Long>   = ctx.corrDataStore.data.map { it[KEY_AGE]  ?: 0L  }

    suspend fun loadCorr(): Double = ctx.corrDataStore.data.first()[KEY_CORR] ?: 0.0
    suspend fun loadAge():  Long   = ctx.corrDataStore.data.first()[KEY_AGE] ?: 0L

    suspend fun save(corr: Double, updatedAtMs: Long) {
        ctx.corrDataStore.edit {
            it[KEY_CORR] = corr
            it[KEY_AGE]  = updatedAtMs
        }
    }
}