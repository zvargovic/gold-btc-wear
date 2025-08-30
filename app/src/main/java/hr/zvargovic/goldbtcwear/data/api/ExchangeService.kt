package hr.zvargovic.goldbtcwear.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import hr.zvargovic.goldbtcwear.data.model.ConvertResponse
import okhttp3.Request

class ExchangeService(
    private val baseUrl: String = ApiConfig.BASE_URL
) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val convertAdapter = moshi.adapter(ConvertResponse::class.java)

    /**
     * Dohvati konverziju 1 FROM u TO (npr. XAU -> EUR, BTC -> EUR)
     */
    fun convert1(from: String, to: String): Result<ConvertResponse> {
        val url = "$baseUrl/convert?from=$from&to=$to&amount=1"
        val req = Request.Builder().url(url).build()
        return runCatching {
            HttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body?.string() ?: error("Empty body")
                convertAdapter.fromJson(body) ?: error("Parse error")
            }
        }
    }
}