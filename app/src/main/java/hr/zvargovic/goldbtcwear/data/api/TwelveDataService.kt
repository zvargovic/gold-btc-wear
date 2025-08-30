package hr.zvargovic.goldbtcwear.data.api

import android.util.Log
import hr.zvargovic.goldbtcwear.BuildConfig
import hr.zvargovic.goldbtcwear.data.model.Quotes
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

/**
 * Twelve Data – jednostavan fetch preko /price endpointa.
 * Simboli:
 *  - BTC/EUR     -> "BTC/EUR"   (crypto)
 *  - XAU/USD     -> "XAU/USD"   (gold spot)
 *  - EUR/USD     -> "EUR/USD"   (forex; za konverziju)
 *
 * XAU/EUR = (XAU/USD) * (USD/EUR) = (XAU/USD) / (EUR/USD)
 */
class TwelveDataService {
    // na vrh klase (unutar class TwelveDataService { ... })
    var lastCreditsUsed: Int? = null
    var lastCreditsLimit: Int? = null
    var lastCreditsLeft: Int? = null
    // opcionalno, ako želiš hard-limit prikaza kad header ne dođe:
    private val defaultMonthlyLimit = 800

    private val base = "https://api.twelvedata.com"

    private fun apiKey(): String {
        val k = BuildConfig.TWELVEDATA_API_KEY
        if (k.isNullOrBlank()) Log.e("GoldBTC", "TwelveData API key missing")
        return k ?: ""
    }

    // zamijeni/uredi httpGet u TwelveDataService
    private fun httpGet(url: String): String? {
        val key = apiKey()
        if (key.isBlank()) return null

        val full = if ('?' in url) "$url&apikey=$key" else "$url?apikey=$key"
        val req = Request.Builder()
            .url(full)
            .header("User-Agent", "GoldBTC-Wear/1.0")
            .build()

        Log.d("GoldBTC", "--> GET $full")
        return try {
            HttpClient.client.newCall(req).execute().use { resp: Response ->
                // --- NEW: pokupi credits headere, ako postoje ---
                // pokušaj razne nazive koje servisi znaju slati
                fun h(name: String) = resp.header(name)
                val candidatesUsed  = listOf("api-credits-used","x-api-credits-used","x-ratelimit-used","x-quota-used","credits-used")
                val candidatesLimit = listOf("api-credits-limit","x-api-credits-limit","x-ratelimit-limit","x-quota-limit","credits-limit")
                val candidatesLeft  = listOf("api-credits-left","x-api-credits-left","x-ratelimit-remaining","x-quota-remaining","credits-left")

                fun parseInt(s: String?): Int? = s?.trim()?.let {
                    Regex("""\d+""").find(it)?.value?.toIntOrNull()
                }

                lastCreditsUsed  = candidatesUsed.firstNotNullOfOrNull { parseInt(h(it)) } ?: lastCreditsUsed
                lastCreditsLimit = candidatesLimit.firstNotNullOfOrNull { parseInt(h(it)) } ?: lastCreditsLimit
                lastCreditsLeft  = candidatesLeft.firstNotNullOfOrNull { parseInt(h(it)) } ?: lastCreditsLeft

                val body = resp.body?.string()

                if (!resp.isSuccessful) {
                    Log.e("GoldBTC", "<-- ${resp.code} $full, body=$body")
                    null
                } else {
                    Log.d("GoldBTC", "<-- 200 $full  | used=${lastCreditsUsed} left=${lastCreditsLeft} limit=${lastCreditsLimit}")
                    body
                }
            }
        } catch (t: Throwable) {
            Log.e("GoldBTC", "HTTP error for $full", t)
            null
        }
    }

    /** Vrati broj iz /price (JSON: {"price":"123.45"}) ili 0.0 ako nema. */
    private fun fetchPrice(symbol: String): Double {
        val body = httpGet("$base/price?symbol=${symbol}")
        if (body.isNullOrEmpty()) return 0.0
        return try {
            val j = JSONObject(body)
            // Error format na Twelve Data zna biti {"code":..., "message":"..."}
            if (j.has("message")) {
                Log.e("GoldBTC", "TwelveData error for $symbol: ${j.optString("message")}")
                0.0
            } else {
                j.optString("price", "0").toDoubleOrNull() ?: 0.0
            }
        } catch (t: Throwable) {
            Log.e("GoldBTC", "Parse /price error for $symbol", t)
            0.0
        }
    }

    private fun fetchBtcEur(): Double {
        // ako iz bilo kojeg razloga BTC/EUR nije dostupan, možeš probati BTC/USD pa konvertirati
        val v = fetchPrice("BTC/EUR")
        if (v > 0.0) return v

        // fallback: BTC/USD -> u EUR
        val btcUsd = fetchPrice("BTC/USD")
        val eurUsd = fetchPrice("EUR/USD")
        return if (btcUsd > 0.0 && eurUsd > 0.0) btcUsd / eurUsd else 0.0
    }

    private fun fetchXauEur(): Double {
        val xauUsd = fetchPrice("XAU/USD")
        val eurUsd = fetchPrice("EUR/USD")
        if (xauUsd <= 0.0 || eurUsd <= 0.0) return 0.0
        val usdEur = 1.0 / eurUsd
        return xauUsd * usdEur
    }

    fun getQuotes(): Quotes {
        val btcEur = fetchBtcEur()
        val xauEur = fetchXauEur()
        val ts = System.currentTimeMillis() / 1000
        Log.d("GoldBTC", "TWELVEDATA XAU/EUR=$xauEur, BTC/EUR=$btcEur, ts=$ts")
        return Quotes(xauEur = xauEur, btcEur = btcEur, ts = ts)
    }
}