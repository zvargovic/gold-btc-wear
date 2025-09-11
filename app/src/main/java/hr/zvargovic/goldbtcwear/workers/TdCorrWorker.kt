package hr.zvargovic.goldbtcwear.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import hr.zvargovic.goldbtcwear.data.CorrectionStore
import hr.zvargovic.goldbtcwear.data.RequestQuotaStore
import hr.zvargovic.goldbtcwear.data.api.TwelveDataService
import hr.zvargovic.goldbtcwear.data.api.YahooService
import hr.zvargovic.goldbtcwear.util.MarketUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

class TdCorrWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "TDWORK"

        // [CHANGE] Throttle pragovi (uskladiti s limitom 800/day; 1 run = ~2 TD requesta)
        private const val MIN_OPEN_MS   = 4 * 60 * 1000L    // 4 min dok je tržište OTVORENO
        private const val MIN_CLOSED_MS = 30 * 60 * 1000L   // 30 min dok je TRŽIŠTE ZATVORENO

        /** Schedules a one-shot work with computed delay (self-reschedule svake ~X min). */
        fun scheduleNext(context: Context, reason: String = "boot/app-start") {
            val now = System.currentTimeMillis()
            val quota = RequestQuotaStore(context)

            runBlocking {
                val lastRun = quota.loadLastRunMs()
                val isOpen = MarketUtils.isMarketOpenUtc(now)
                val minGap = if (isOpen) MIN_OPEN_MS else MIN_CLOSED_MS

                val byGap = if (lastRun == 0L) now else lastRun + minGap
                val byMarket = nextMarketOpenFrom(now)
                var nextTs = max(byGap, byMarket)
                if (nextTs <= now) nextTs = now + 1_000 // minimalni delay

                val delayMs = (nextTs - now).coerceAtLeast(1_000L)
                val h = TimeUnit.MILLISECONDS.toHours(delayMs)
                val m = TimeUnit.MILLISECONDS.toMinutes(delayMs) % 60
                val s = TimeUnit.MILLISECONDS.toSeconds(delayMs) % 60

                Log.i(
                    TAG,
                    "scheduleNext[$reason]: in ${h}h ${m}m ${s}s " +
                            "(isOpen=$isOpen minGap=${minGap/60000}m " +
                            "byGap=${fmtAgoOrEta(now, byGap)} byMarket=${fmtAgoOrEta(now, byMarket)} " +
                            "lastRun=${if (lastRun==0L) "n/a" else "${minutesAgo(now,lastRun)}m ago"})"
                )

                val req = OneTimeWorkRequestBuilder<TdCorrWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .addTag(TAG)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "TdCorrWorker@unique",
                    ExistingWorkPolicy.REPLACE,
                    req
                )
            }
        }

        private fun minutesAgo(now: Long, ts: Long) =
            TimeUnit.MILLISECONDS.toMinutes(now - ts)

        private fun fmtAgoOrEta(now: Long, ts: Long): String {
            return if (ts >= now) {
                val m = TimeUnit.MILLISECONDS.toMinutes(ts - now)
                "~${m}m"
            } else {
                val m = TimeUnit.MILLISECONDS.toMinutes(now - ts)
                "${m}m ago"
            }
        }

        /** Brza procjena prvog sljedećeg OPEN trenutka (pokriva pauzu 22–23 UTC + vikend). */
        private fun nextMarketOpenFrom(now: Long): Long {
            if (MarketUtils.isMarketOpenUtc(now)) return now
            var t = now
            val limit = now + 7 * 24 * 60 * 60 * 1000L
            while (t < limit && !MarketUtils.isMarketOpenUtc(t)) {
                t += 60_000L // +1 min
            }
            return t
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val quota = RequestQuotaStore(ctx)
        val corrStore = CorrectionStore(ctx)
        val yahoo = YahooService()

        val now = System.currentTimeMillis()
        val isOpen = MarketUtils.isMarketOpenUtc(now)
        val minGap = if (isOpen) MIN_OPEN_MS else MIN_CLOSED_MS
        val dayCount = quota.loadDayCount(now)
        val lastRun = quota.loadLastRunMs()

        Log.i(
            TAG,
            "doWork: start  isOpen=$isOpen  dayUsed=$dayCount/${RequestQuotaStore.DAILY_LIMIT}  " +
                    "lastRun=${if (lastRun==0L) "n/a" else "${TimeUnit.MILLISECONDS.toMinutes(now-lastRun)}m ago"}"
        )

        // [CHANGE] DNEVNI LIMIT — TwelveData free ≈ 800/day (računamo svaki TD HTTP hit)
        if (dayCount >= RequestQuotaStore.DAILY_LIMIT) {
            Log.w(TAG, "Throttle: DAILY_LIMIT reached (${RequestQuotaStore.DAILY_LIMIT}/day) — skip fetch.")
            scheduleNext(ctx, "daily-limit")
            return@withContext Result.success()
        }

        // [CHANGE] Gap ograničenje
        if (lastRun > 0 && now - lastRun < minGap) {
            val leftMs = (lastRun + minGap - now).coerceAtLeast(0L)
            val mm = TimeUnit.MILLISECONDS.toMinutes(leftMs)
            Log.i(TAG, "Throttle: too soon (minGap=${minGap/60000}m). Wait ≈ ${mm}m — skip.")
            scheduleNext(ctx, "min-gap")
            return@withContext Result.success()
        }

        // [CHANGE] Tržište zatvoreno ⇒ samo reschedule do prvog OPEN
        if (!isOpen) {
            val openAt = Companion.nextMarketOpenFrom(now)
            val inMin = TimeUnit.MILLISECONDS.toMinutes(openAt - now)
            Log.i(TAG, "Market closed. Next open in ~${inMin}m — skip fetch.")
            scheduleNext(ctx, "market-closed")
            return@withContext Result.success()
        }

        // ==== FETCH ====
        val apiKey = fallbackApiKeyFromPrefs(ctx)
        if (apiKey.isNullOrBlank()) {
            Log.w(TAG, "No TD apiKey — keep old K; only Yahoo runs.")
            quota.touchRun(now, success = false)
            scheduleNext(ctx, "no-api-key")
            return@withContext Result.success()
        } else {
            val safe = apiKey.take(3) + "…" + apiKey.takeLast(3)
            Log.i(TAG, "Using TD apiKey=$safe")
        }

        // [CHANGE] TD servis s brojanjem SVAKOG HTTP poziva (+1 na dayUsed)
        val td = TwelveDataService(onRequest = {
            runBlocking { quota.bumpDay(1) }
        })

        // 1) Yahoo EUR/oz
        val yahooEur = yahoo.getSpotEur().getOrElse {
            Log.w(TAG, "Yahoo fetch failed: ${it.message}")
            quota.touchRun(now, success = false)
            scheduleNext(ctx, "yahoo-fail")
            return@withContext Result.success()
        }

        // 2) TD (komponente)
        val comps = td.getSpotEurComponents(apiKey).getOrElse {
            Log.w(TAG, "TD fetch failed: ${it.message}")
            quota.touchRun(now, success = false)
            scheduleNext(ctx, "td-fail")
            return@withContext Result.success()
        }
        val tdEur = comps.xauEur

        // [CHANGE] Eksplicitni logovi za usporedbu izvora
        Log.i(
            TAG,
            "fetch: YAHOO  xauEur=${"%.2f".format(yahooEur)}  |  TD  xauUsd=${"%.4f".format(comps.xauUsd)}  eurUsd=${"%.6f".format(comps.eurUsd)}  xauEur=${"%.2f".format(tdEur)}"
        )

        if (!yahooEur.isFinite() || !tdEur.isFinite() || yahooEur <= 0.0) {
            Log.w(TAG, "Bad numbers: yahoo=$yahooEur td=$tdEur")
            quota.touchRun(now, success = false)
            scheduleNext(ctx, "bad-numbers")
            return@withContext Result.success()
        }

        val k = tdEur / yahooEur - 1.0
        if (abs(k) > 0.10) {
            Log.w(TAG, "Unrealistic K=${"%.4f".format(k)} — skip save.")
            quota.touchRun(now, success = false)
            scheduleNext(ctx, "k-unreal")
            return@withContext Result.success()
        }

        try {
            corrStore.save(corr = k, updatedAtMs = System.currentTimeMillis())
            quota.touchRun(now, success = true)

            Log.i(
                TAG,
                "Saved K=${"%.4f".format(k)}  | TD: XAU/USD=${"%.4f".format(comps.xauUsd)}  EUR/USD=${"%.6f".format(comps.eurUsd)}  (EUR/oz=${"%.2f".format(tdEur)})"
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Save K failed: ${e.message}")
        }

        // [CHANGE] uvijek zakaži sljedeći run prema pravilima
        scheduleNext(ctx, "after-success")
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