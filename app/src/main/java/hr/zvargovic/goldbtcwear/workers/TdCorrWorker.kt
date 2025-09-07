package hr.zvargovic.goldbtcwear.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import hr.zvargovic.goldbtcwear.data.CorrectionStore
import hr.zvargovic.goldbtcwear.data.RequestQuotaStore // [NOVO]
import hr.zvargovic.goldbtcwear.data.api.TwelveDataService
import hr.zvargovic.goldbtcwear.data.api.YahooService
import hr.zvargovic.goldbtcwear.util.MarketUtils // [NOVO]
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class TdCorrWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val tag = "TDWORK"

    // [NOVO] tuning
    private val DAILY_CAP = 24                  // cilj po danu (lokalno ograničenje)
    private val MIN_OPEN_MS = 30 * 60 * 1000L   // 30 min kad je tržište otvoreno
    private val MIN_CLOSED_MS = 3 * 60 * 60 * 1000L // 3 h kad je zatvoreno

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val ctx = applicationContext
        val quota = RequestQuotaStore(ctx)      // [NOVO]
        val corrStore = CorrectionStore(ctx)
        val yahoo = YahooService()

        // Dinamički throttle [NOVO]
        val isOpen = MarketUtils.isMarketOpenUtc(now)
        val minGap = if (isOpen) MIN_OPEN_MS else MIN_CLOSED_MS
        val dayCount = quota.loadDayCount()
        val lastRun = quota.loadLastRunMs()

        if (dayCount >= DAILY_CAP) {
            Log.i(tag, "Throttle: reached DAILY_CAP=$DAILY_CAP (dayCount=$dayCount) — skip.")
            return@withContext Result.success()
        }
        if (lastRun > 0L && now - lastRun < minGap) {
            val leftMin = ((minGap - (now - lastRun)) / 60000.0).toInt()
            Log.i(tag, "Throttle: too soon (isOpen=$isOpen). Wait ~${leftMin}m — skip.")
            return@withContext Result.success()
        }

        Log.i(tag, "TdCorrWorker started. isOpen=$isOpen minGapMs=$minGap dayCount=$dayCount")

        val apiKey = fallbackApiKeyFromPrefs(ctx)
        if (apiKey.isNullOrBlank()) {
            Log.w(tag, "No TD apiKey – keeping old K.")
            quota.touchRun(now) // zapiši pokušaj (da ne spamamo)
            return@withContext Result.success()
        } else {
            val safeKey = apiKey.take(3) + "…" + apiKey.takeLast(3)
            Log.i(tag, "API key source = DataStore/Prefs ($safeKey)")
        }

        // [NOVO] TD servis s brojilom req-a
        val td = TwelveDataService(onRequest = {
            // svaka TD HTTP hit = +1
            // bump može uzrokovati rollover (dan/mjesec)
            kotlinx.coroutines.runBlocking { quota.bump(1) }
        })

        // --- Yahoo (EUR/oz)
        val yahooEur = yahoo.getSpotEur().getOrElse {
            Log.w(tag, "Yahoo fetch failed: ${it.message}")
            quota.touchRun(now)
            return@withContext Result.success()
        }

        // --- TD (komponente + EUR/oz) — unutar toga se broje req-ovi (2 komada)
        val comps = td.getSpotEurComponents(apiKey).getOrElse {
            Log.w(tag, "TD fetch failed: ${it.message}")
            quota.touchRun(now)
            return@withContext Result.success()
        }
        val tdEur = comps.xauEur

        if (!yahooEur.isFinite() || !tdEur.isFinite() || yahooEur <= 0.0) {
            Log.w(tag, "Bad numbers: yahoo=$yahooEur td=$tdEur")
            quota.touchRun(now)
            return@withContext Result.success()
        }

        val k = tdEur / yahooEur - 1.0
        if (abs(k) > 0.10) {
            Log.w(tag, "Unrealistic K=${"%.4f".format(k)} – skip save.")
            quota.touchRun(now)
            return@withContext Result.success()
        }

        try {
            corrStore.save(corr = k, updatedAtMs = System.currentTimeMillis())
            quota.touchRun(now) // [NOVO] zabilježimo uspješan run
            Log.i(
                tag,
                "Saved K=${"%.4f".format(k)}  (TD: XAU/USD=${"%.4f".format(comps.xauUsd)} EUR/USD=${"%.6f".format(comps.eurUsd)} => ${"%.2f".format(tdEur)}  |  Y=${"%.2f".format(yahooEur)})"
            )
        } catch (e: Throwable) {
            Log.w(tag, "Save K failed: ${e.message}")
        }

        Result.success()
    }

    private fun fallbackApiKeyFromPrefs(ctx: Context): String? {
        val candidates = listOf(
            "settings" to "api_key",
            "settings" to "apiKey",
            "gold_settings" to "api_key",
            "gold_settings" to "apiKey"
        )
        for ((file, key) in candidates) {
            val v = ctx.getSharedPreferences(file, Context.MODE_PRIVATE).getString(key, null)
            if (!v.isNullOrBlank()) return v
        }
        return null
    }
}