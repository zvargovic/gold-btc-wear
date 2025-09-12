package hr.zvargovic.goldbtcwear.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Wilder RSI (default period = 14).
 * Ulaz su close cijene poredane od najstarije prema najnovijoj.
 * Vraća RSI u rasponu 0f..100f ili null ako nema dovoljno uzoraka.
 */
object RsiCalc {
    fun compute(closes: List<Double>, period: Int = 14): Float? {
        if (closes.size < period + 1) return null
        var gain = 0.0
        var loss = 0.0

        // inicijalni prosjek
        for (i in 1..period) {
            val diff = closes[i] - closes[i - 1]
            if (diff >= 0) gain += diff else loss -= diff
        }
        var avgGain = gain / period
        var avgLoss = loss / period

        // Wilder smoothing kroz ostatak serije
        for (i in (period + 1) until closes.size) {
            val diff = closes[i] - closes[i - 1]
            val g = if (diff > 0) diff else 0.0
            val l = if (diff < 0) -diff else 0.0
            avgGain = (avgGain * (period - 1) + g) / period
            avgLoss = (avgLoss * (period - 1) + l) / period
        }

        if (avgGain == 0.0 && avgLoss == 0.0) return 50f
        if (avgLoss == 0.0) return 100f
        val rs = avgGain / avgLoss
        val rsi = 100.0 - (100.0 / (1.0 + rs))
        if (rsi.isNaN() || rsi.isInfinite()) return null
        return rsi.toFloat().coerceIn(0f, 100f)
    }
}