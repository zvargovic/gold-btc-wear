package hr.zvargovic.goldbtcwear.domain.usecase
import hr.zvargovic.goldbtcwear.data.model.Quotes
class GetQuotesUseCase { suspend operator fun invoke(): Quotes = Quotes() }