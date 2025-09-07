package hr.zvargovic.goldbtcwear.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * Twelve Data free:
 *  - https://api.twelvedata.com/price?symbol=XAU/USD&apikey=KEY  -> {"price":"..."}
 *  - https://api.twelvedata.com/price?symbol=EUR/USD&apikey=KEY  -> {"price":"..."}
 * EUR = (XAU/USD) / (EUR/USD)
 */
class TwelveDataService(
    private val onRequest: (() -> Unit)? = null   // <— NOVO: callback po HTTP requestu
) {

    private val tag = "TDAPI"
    private val baseUrl = "https://api.twelvedata.com/price"

    data class TdComponents(
        val xauUsd: Double,
        val eurUsd: Double,
        val xauEur: Double
    )

    suspend fun getSpotEur(apiKey: String): Result<Double> = withContext(Dispatchers.IO) {
        runCatching {
            val comps = getSpotEurComponents(apiKey).getOrThrow()
            Log.i(
                tag,
                "XAU/USD=${"%.4f".format(comps.xauUsd)}  EUR/USD=${"%.6f".format(comps.eurUsd)}  => XAU/EUR=${"%.2f".format(comps.xauEur)}"
            )
            comps.xauEur
        }
    }

    suspend fun getSpotEurComponents(apiKey: String): Result<TdComponents> = withContext(Dispatchers.IO) {
        runCatching {
            val xauUsd = fetchPrice("XAU/USD", apiKey)
            val eurUsd = fetchPrice("EUR/USD", apiKey)
            if (!xauUsd.isFinite() || !eurUsd.isFinite() || eurUsd <= 0.0) {
                error("Bad TD data xauUsd=$xauUsd eurUsd=$eurUsd")
            }
            TdComponents(
                xauUsd = xauUsd,
                eurUsd = eurUsd,
                xauEur = xauUsd / eurUsd
            )
        }
    }

    private fun fetchPrice(symbol: String, apiKey: String): Double {
        val url = "$baseUrl?symbol=${symbol}&apikey=${apiKey}"
        val safeKey = apiKey.take(3) + "…" + apiKey.takeLast(3)
        Log.d(tag, "GET $symbol (key=$safeKey)")

        // Broji svaki request
        try { onRequest?.invoke() } catch (_: Throwable) {}

        val req = Request.Builder().url(url).build()
        HttpClient.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} for $symbol")
            val body = resp.body?.string() ?: error("Empty body for $symbol")
            val json = JSONObject(body)

            if (json.has("status") && json.optString("status") == "error") {
                val msg = json.optString("message", "unknown")
                error("TD error for $symbol: $msg")
            }

            val priceStr = json.optString("price")
            if (priceStr.isNullOrEmpty()) error("No price for $symbol (raw=$body)")
            val v = priceStr.toDouble()
            Log.d(tag, "$symbol = $v")
            return v
        }
    }
}