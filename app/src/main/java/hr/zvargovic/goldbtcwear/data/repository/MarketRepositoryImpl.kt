package hr.zvargovic.goldbtcwear.data.repository

import hr.zvargovic.goldbtcwear.data.api.TwelveDataService
import hr.zvargovic.goldbtcwear.data.model.Quotes

class MarketRepositoryImpl(
    private val service: TwelveDataService = TwelveDataService()
) {
    fun getQuotes(): Quotes = service.getQuotes()
}