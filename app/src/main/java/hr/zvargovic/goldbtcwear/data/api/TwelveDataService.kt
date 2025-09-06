package hr.zvargovic.goldbtcwear.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import android.util.Log

private const val TAG_TD = "TD"

class TwelveDataService {

    suspend fun getSpotEur(apiKey: String): Result<Double> = withContext(Dispatchers.IO) {
        runCatching {
            Log.i(TAG_TD, "spot: start fetch (XAU/USD & EUR/USD)")
            val xauUsd = fetchPrice("XAU/USD", apiKey)
            val eurUsd = fetchPrice("EUR/USD", apiKey)
            Log.d(TAG_TD, "spot: xauUsd=$xauUsd eurUsd=$eurUsd")

            if (!xauUsd.isFinite() || !eurUsd.isFinite() || eurUsd <= 0.0) {
                error("Bad TD data")
            }
            val eur = xauUsd / eurUsd
            Log.i(TAG_TD, "spot: OK eur=$eur")
            eur
        }.onFailure { e ->
            Log.w(TAG_TD, "spot: FAIL ${e.message}")
        }
    }

    private fun fetchPrice(symbol: String, apiKey: String): Double {
        val url = "https://api.twelvedata.com/price?symbol=${symbol}&apikey=${apiKey}"
        Log.d(TAG_TD, "req: $symbol -> $url")
        val req = Request.Builder().url(url).build()
        HttpClient.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body?.string() ?: error("Empty body")
            val json = JSONObject(body)
            val priceStr = json.optString("price")
            if (priceStr.isNullOrEmpty()) error("No price")
            val p = priceStr.toDouble()
            Log.v(TAG_TD, "resp: $symbol = $p")
            return p
        }
    }
}