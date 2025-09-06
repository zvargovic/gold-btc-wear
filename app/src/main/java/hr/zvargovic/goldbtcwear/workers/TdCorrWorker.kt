package hr.zvargovic.goldbtcwear.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import hr.zvargovic.goldbtcwear.data.CorrectionStore
import hr.zvargovic.goldbtcwear.data.SettingsStore
import hr.zvargovic.goldbtcwear.data.api.TwelveDataService
import hr.zvargovic.goldbtcwear.data.api.YahooService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.abs

class TdCorrWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val tag = "TDWORK"

    private fun mask(s: String?) = if (s.isNullOrBlank()) "(null)" else
        s.take(3) + "…" + s.takeLast(minOf(4, s.length))

    private suspend fun resolveApiKey(ctx: Context): String? {
        return try {
            val ds = SettingsStore(ctx)
            val fromDs = ds.apiKeyFlow.first()
            if (!fromDs.isNullOrBlank()) {
                Log.i(tag, "API key source = DataStore (${mask(fromDs)})")
                fromDs
            } else {
                val sp = fallbackApiKeyFromPrefs(ctx)
                Log.i(tag, "API key source = SharedPrefs (${mask(sp)})")
                sp
            }
        } catch (_: Throwable) {
            val sp = fallbackApiKeyFromPrefs(ctx)
            Log.i(tag, "API key source = SharedPrefs (${mask(sp)})")
            sp
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(tag, "TdCorrWorker started.")

        val ctx = applicationContext
        val corrStore = CorrectionStore(ctx)
        val td = TwelveDataService()
        val yahoo = YahooService()

        val apiKey = resolveApiKey(ctx)
        if (apiKey.isNullOrBlank()) {
            Log.w(tag, "No TD apiKey – keeping old K.")
            return@withContext Result.success()
        }

        val yahooRes = yahoo.getSpotEur()
        val tdRes = td.getSpotEur(apiKey)

        val yahooEur = yahooRes.getOrElse {
            Log.w(tag, "Yahoo fetch failed: ${it.message}")
            return@withContext Result.success()
        }
        val tdEur = tdRes.getOrElse {
            Log.w(tag, "TD fetch failed: ${it.message}")
            return@withContext Result.success()
        }

        if (!yahooEur.isFinite() || !tdEur.isFinite() || yahooEur <= 0.0) {
            Log.w(tag, "Bad numbers: yahoo=$yahooEur td=$tdEur")
            return@withContext Result.success()
        }

        val k = tdEur / yahooEur - 1.0

        if (abs(k) > 0.10) { // safety 10%
            Log.w(tag, "Unrealistic K=${"%.4f".format(k)} – skip save.")
            return@withContext Result.success()
        }

        try {
            corrStore.save(
                corr = k,
                updatedAtMs = System.currentTimeMillis()
            )
            Log.i(
                tag,
                "Saved K=${"%.4f".format(k)} (td=${"%.2f".format(tdEur)}, yahoo=${"%.2f".format(yahooEur)})"
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