package hr.zvargovic.goldbtcwear.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hr.zvargovic.goldbtcwear.data.repository.MarketRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.DecimalFormat

data class PricesUiState(
    val xauEur: String = "--",
    val btcEur: String = "--",
    val updated: String = "--",
    val loading: Boolean = false,
    val error: String? = null
)

class PricesViewModel(
    private val repo: MarketRepositoryImpl = MarketRepositoryImpl()
) : ViewModel() {

    private val df = DecimalFormat("#,##0.00")
    private val _ui = MutableStateFlow(PricesUiState(loading = true))
    val ui: StateFlow<PricesUiState> = _ui

    init { refresh() }

    fun refresh() {
        _ui.value = _ui.value.copy(loading = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.getQuotes() }
                .onSuccess { q ->
                    _ui.value = PricesUiState(
                        xauEur = df.format(q.xauEur),
                        btcEur = df.format(q.btcEur),
                        updated = unixToTime(q.ts),
                        loading = false
                    )
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(
                        loading = false,
                        error = e.message ?: "Fetch failed"
                    )
                }
        }
    }

    private fun unixToTime(ts: Long): String {
        val sec = if (ts > 1_000_000_000L) ts else ts // već je u sekundama
        val d = java.util.Date(sec * 1000)
        val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return fmt.format(d)
    }
}