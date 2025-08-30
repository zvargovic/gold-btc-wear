package hr.zvargovic.goldbtcwear.data.model

import com.squareup.moshi.Json

data class ConvertResponse(
    val success: Boolean? = null,
    val query: Query? = null,
    val info: Info? = null,
    val result: Double? = null,      // očekujemo cijenu (EUR po 1 XAU/BTC)
    val date: String? = null
) {
    data class Query(
        val from: String? = null,
        val to: String? = null,
        val amount: Double? = null
    )
    data class Info(
        val rate: Double? = null,
        @Json(name = "timestamp") val ts: Long? = null
    )
}