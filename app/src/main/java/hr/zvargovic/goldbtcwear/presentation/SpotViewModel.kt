package hr.zvargovic.goldbtcwear.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hr.zvargovic.goldbtcwear.data.SettingsStore
import hr.zvargovic.goldbtcwear.data.api.TwelveDataService
import hr.zvargovic.goldbtcwear.data.api.YahooService
import hr.zvargovic.goldbtcwear.data.repository.PriceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class PriceService { Yahoo, TwelveData }

class SpotViewModel(app: Application) : AndroidViewModel(app) {

    private val _ts = MutableStateFlow<Long?>(null)
    val ts: StateFlow<Long?> = _ts

    private val repo = PriceRepository(
        yahoo = YahooService(),
        td = TwelveDataService(),
        settings = SettingsStore(app)
    )

    private val _eur = MutableStateFlow<Double?>(null)
    val eur: StateFlow<Double?> = _eur

    private val _service = MutableStateFlow(PriceService.Yahoo)
    val service: StateFlow<PriceService> = _service

    fun toggleService() {
        _service.value = if (_service.value == PriceService.Yahoo)
            PriceService.TwelveData else PriceService.Yahoo
    }

    fun start() {
        viewModelScope.launch {
            while (true) {
                val value = when (_service.value) {
                    PriceService.Yahoo -> repo.fetchYahooEur()
                    PriceService.TwelveData -> repo.fetchTwelveDataEur() ?: repo.fetchYahooEur()
                }
                value?.let {
                    _eur.value = it
                    _ts.value  = System.currentTimeMillis()   // ➋ ZAPIS vremena zadnjeg uspješnog dohvata
                }
                delay(20_000)
            }
        }
    }
}