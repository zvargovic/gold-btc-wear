package hr.zvargovic.goldbtcwear.data.repository
import hr.zvargovic.goldbtcwear.data.model.Quotes
class MarketRepositoryImpl { suspend fun getQuotes(): Quotes = Quotes() }