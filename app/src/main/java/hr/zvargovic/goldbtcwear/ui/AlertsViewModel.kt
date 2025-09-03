package hr.zvargovic.goldbtcwear.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

data class PriceAlert(val id: Long = System.nanoTime(), val value: Double)

class AlertsViewModel : ViewModel() {
    var spotNow by mutableStateOf(2315.40)
        private set

    val alerts = mutableStateListOf<PriceAlert>()

    fun addAlert(value: Double) {
        alerts.add(PriceAlert(value = value))
    }

    fun removeAlert(id: Long) {
        alerts.removeAll { it.id == id }
    }

    fun setSpot(value: Double) {
        spotNow = value
    }
}