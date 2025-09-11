package hr.zvargovic.goldbtcwear.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

private val Context.reqDataStore by preferencesDataStore(name = "req_quota_prefs")

class RequestQuotaStore(private val ctx: Context) {

    companion object {
        // TwelveData FREE: 800 / DAY
        const val DAILY_LIMIT = 800

        private val KEY_DAY_USED     = intPreferencesKey("day_used")
        private val KEY_DAY_KEY      = intPreferencesKey("day_key")      // npr. 20250909
        private val KEY_LAST_RUN_MS  = longPreferencesKey("last_run_ms")
    }

    // PUBLIC FLOWS (za UI)
    val dayUsedFlow: Flow<Int> =
        ctx.reqDataStore.data.map { it[KEY_DAY_USED] ?: 0 }
    val lastRunMsFlow: Flow<Long> =
        ctx.reqDataStore.data.map { it[KEY_LAST_RUN_MS] ?: 0L }

    // === helpers: UTC dan ključ ===
    private fun yyyymmddUtc(ts: Long): Int {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = ts
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return y * 10000 + m * 100 + d
    }

    /** Vrati dayUsed za današnji dan (uz rollover). */
    suspend fun loadDayCount(nowMs: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        val dayKeyNow = yyyymmddUtc(nowMs)
        var out = 0
        ctx.reqDataStore.edit { prefs ->
            val storedKey = prefs[KEY_DAY_KEY] ?: -1
            if (storedKey != dayKeyNow) {
                prefs[KEY_DAY_KEY] = dayKeyNow
                prefs[KEY_DAY_USED] = 0
                out = 0
            } else {
                out = prefs[KEY_DAY_USED] ?: 0
            }
        }
        out
    }

    /** Samo pročitaj lastRunMs. */
    suspend fun loadLastRunMs(): Long =
        ctx.reqDataStore.data.map { it[KEY_LAST_RUN_MS] ?: 0L }.first()

    /** Inkrementiraj dnevni brojač za +n (npr. svaki TD HTTP hit). */
    suspend fun bumpDay(n: Int, nowMs: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val dayKeyNow = yyyymmddUtc(nowMs)
        ctx.reqDataStore.edit { prefs ->
            val storedKey = prefs[KEY_DAY_KEY] ?: -1
            if (storedKey != dayKeyNow) {
                prefs[KEY_DAY_KEY] = dayKeyNow
                prefs[KEY_DAY_USED] = 0
            }
            val cur = prefs[KEY_DAY_USED] ?: 0
            prefs[KEY_DAY_USED] = (cur + n).coerceAtLeast(0)
        }
    }

    /** Zabilježi pokušaj run-a Workera (i eventualno +1 uspjeh za dnevni count). */
    suspend fun touchRun(nowMs: Long = System.currentTimeMillis(), success: Boolean) = withContext(Dispatchers.IO) {
        val dayKeyNow = yyyymmddUtc(nowMs)
        ctx.reqDataStore.edit { prefs ->
            prefs[KEY_LAST_RUN_MS] = nowMs
            val storedKey = prefs[KEY_DAY_KEY] ?: -1
            if (storedKey != dayKeyNow) {
                prefs[KEY_DAY_KEY] = dayKeyNow
                prefs[KEY_DAY_USED] = 0
            }
            if (success) {
                val d = prefs[KEY_DAY_USED] ?: 0
                prefs[KEY_DAY_USED] = d + 1
            }
        }
    }
}