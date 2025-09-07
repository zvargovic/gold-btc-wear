package hr.zvargovic.goldbtcwear.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * Y! Finance neslužbeni: /v8/finance/chart/{SYMBOL}
 * - GC=F (COMEX Gold futures, USD)
 * - EURUSD=X (FX, USD per EUR)
 *
 * Spot u EUR ≈ GC=F(USD) / EURUSD=X
 */
class YahooService {

    private val tag = "YAPI"

    suspend fun getGoldFutureUsd(): Result<Double> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/GC=F"
            val req = Request.Builder().url(url).build()
            HttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body?.string() ?: error("Empty body")
                val v = parseRegularMarketPriceFromChart(body)
                Log.d(tag, "GC=F USD/oz = $v")
                v
            }
        }
    }

    suspend fun getEurUsd(): Result<Double> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/EURUSD=X"
            val req = Request.Builder().url(url).build()
            HttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body?.string() ?: error("Empty body")
                val v = parseRegularMarketPriceFromChart(body)
                Log.d(tag, "EURUSD=X USD per EUR = $v")
                v
            }
        }
    }

    /** EUR cijena ≈ futures(USD) / (USD per EUR) */
    suspend fun getSpotEur(): Result<Double> = withContext(Dispatchers.IO) {
        val fut = getGoldFutureUsd().getOrElse { return@withContext Result.failure(it) }
        val eurusd = getEurUsd().getOrElse { return@withContext Result.failure(it) }
        if (!fut.isFinite() || !eurusd.isFinite() || eurusd <= 0.0) {
            return@withContext Result.failure(IllegalStateException("Bad data"))
        }
        val eur = fut / eurusd
        Log.i(tag, "GC=F=${"%.2f".format(fut)}  EURUSD=X=${"%.6f".format(eurusd)}  => XAU/EUR=${"%.2f".format(eur)}")
        Result.success(eur)
    }

    private fun parseRegularMarketPriceFromChart(body: String): Double {
        val root = JSONObject(body)
        val chart = root.getJSONObject("chart")
        chart.optJSONObject("error")?.let { err ->
            error("Yahoo error: ${err.optString("code")} ${err.optString("description")}")
        }
        val result = chart.getJSONArray("result")
        if (result.length() == 0) error("Empty result")
        val meta = result.getJSONObject(0).getJSONObject("meta")
        if (!meta.has("regularMarketPrice")) error("No regularMarketPrice")
        return meta.getDouble("regularMarketPrice")
    }
}