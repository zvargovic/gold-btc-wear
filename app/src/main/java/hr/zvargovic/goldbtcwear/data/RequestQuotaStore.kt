package hr.zvargovic.goldbtcwear.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

private val Context.reqDataStore by preferencesDataStore(name = "req_quota_prefs")

/**
 * Pamti:
 *  - monthlyUsed: broj TD requestova u tekućem mjesecu (rollover na promjenu mjeseca)
 *  - monthlyKey: YYYYMM ključ trenutnog mjeseca
 *  - dayUsed: broj uspješnih "run-ova" workera taj dan (za lokalni DAILY_CAP)
 *  - dayKey: YYYYMMDD ključ trenutnog dana
 *  - lastRunMs: zadnji timestamp kad je worker *pokušao* (uspješno ili ne)
 */
class RequestQuotaStore(private val ctx: Context) {

    companion object {
        private val KEY_MONTHLY_USED = intPreferencesKey("monthly_used")
        private val KEY_MONTHLY_KEY  = intPreferencesKey("monthly_key")   // npr. 202509
        private val KEY_DAY_USED     = intPreferencesKey("day_used")      // broj uspješnih run-ova (za DAILY_CAP)
        private val KEY_DAY_KEY      = intPreferencesKey("day_key")       // npr. 20250907
        private val KEY_LAST_RUN_MS  = longPreferencesKey("last_run_ms")
    }

    /** Po želji držiš u UI-u kao konstanta */
    val monthlyQuotaLimit: Int = 800

    // --- Public flows (za UI, ako želiš)
    val monthlyUsedFlow: Flow<Int> =
        ctx.reqDataStore.data.map { it[KEY_MONTHLY_USED] ?: 0 }

    val dayUsedFlow: Flow<Int> =
        ctx.reqDataStore.data.map { it[KEY_DAY_USED] ?: 0 }

    val lastRunMsFlow: Flow<Long> =
        ctx.reqDataStore.data.map { it[KEY_LAST_RUN_MS] ?: 0L }

    // --- Helpers: datum ključevi u UTC
    private fun yyyymmUtc(ts: Long): Int {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = ts
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        return y * 100 + m
    }

    private fun yyyymmddUtc(ts: Long): Int {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = ts
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return y * 10000 + m * 100 + d
    }

    /** Vraća dayUsed za današnji dan (uz rollover ako treba). */
    suspend fun loadDayCount(nowMs: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        val dayKeyNow = yyyymmddUtc(nowMs)
        var out = 0
        ctx.reqDataStore.edit { prefs ->
            val storedKey = prefs[KEY_DAY_KEY] ?: -1
            if (storedKey != dayKeyNow) {
                // rollover dana
                prefs[KEY_DAY_KEY] = dayKeyNow
                prefs[KEY_DAY_USED] = 0
                out = 0
            } else {
                out = prefs[KEY_DAY_USED] ?: 0
            }
        }
        out
    }

    /** Vraća lastRunMs bez pisanja. */
    suspend fun loadLastRunMs(): Long = withContext(Dispatchers.IO) {
        var out = 0L
        ctx.reqDataStore.data.map { it[KEY_LAST_RUN_MS] ?: 0L }.collect { v ->
            out = v; return@collect
        }
        out
    }

    /** Inkrementira monthlyUsed za +n, s rolloverom mjeseca. */
    suspend fun bump(n: Int, nowMs: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val monthNow = yyyymmUtc(nowMs)
        ctx.reqDataStore.edit { prefs ->
            val storedMonth = prefs[KEY_MONTHLY_KEY] ?: -1
            if (storedMonth != monthNow) {
                // rollover mjeseca
                prefs[KEY_MONTHLY_KEY] = monthNow
                prefs[KEY_MONTHLY_USED] = 0
            }
            val cur = prefs[KEY_MONTHLY_USED] ?: 0
            prefs[KEY_MONTHLY_USED] = (cur + n).coerceAtLeast(0)
        }
    }

    /**
     * Označi da je worker *pokušao* (uspješno ili ne); koristi se i za throttle.
     * Ako želiš brojati samo uspješne run-ove u dayUsed, proslijedi success=true.
     */
    suspend fun touchRun(nowMs: Long = System.currentTimeMillis(), success: Boolean = true) = withContext(Dispatchers.IO) {
        val dayNow = yyyymmddUtc(nowMs)
        ctx.reqDataStore.edit { prefs ->
            // lastRun se uvijek zapisuje
            prefs[KEY_LAST_RUN_MS] = nowMs

            // rollover dana + inkrement dayUsed samo ako success==true
            val storedDay = prefs[KEY_DAY_KEY] ?: -1
            if (storedDay != dayNow) {
                prefs[KEY_DAY_KEY] = dayNow
                prefs[KEY_DAY_USED] = 0
            }
            if (success) {
                val d = prefs[KEY_DAY_USED] ?: 0
                prefs[KEY_DAY_USED] = d + 1
            }
        }
    }
}