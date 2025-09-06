package hr.zvargovic.goldbtcwear.data.repository

import hr.zvargovic.goldbtcwear.data.SettingsStore
import hr.zvargovic.goldbtcwear.data.api.TwelveDataService
import hr.zvargovic.goldbtcwear.data.api.YahooService

class PriceRepository(
    private val yahoo: YahooService,
    private val td: TwelveDataService,
    private val settings: SettingsStore
) {

    /** Y! (futures→EUR) */
    suspend fun fetchYahooEur(): Double? =
        yahoo.getSpotEur().getOrNull()

    /** Twelve Data direktno → EUR (ako postoji API ključ) */
    suspend fun fetchTwelveDataEur(): Double? {
        val key = settings.loadApiKey()
        if (key.isBlank()) return null
        return td.getSpotEur(key).getOrNull()
    }
}