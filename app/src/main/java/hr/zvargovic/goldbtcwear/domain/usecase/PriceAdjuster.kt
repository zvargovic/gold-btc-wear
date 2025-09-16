package hr.zvargovic.goldbtcwear.domain.usecase

/**
 * Jedino mjesto gdje definiraš korekciju:
 *  - FIXED_EUR: fiksni iznos u EUR/oz (npr. -5.0 znači -5 EUR)
 *  - PCT_SLOPE: postotna korekcija kao faktor (npr. -0.0012 = -0.12%)
 *
 * PRIMJENJUJE SE NA FINALNU CIJENU KOJA IDE NA EKRAN / ALARME / TILE.
 */
object PriceAdjuster {

    /** HARDKODIRANO — ovdje promijeniš kad mjerenje završi. */
    private const val FIXED_EUR: Double = -5.0        // npr. -5 EUR
    private const val PCT_SLOPE: Double = -0.0012     // npr. -0.12% = -0.0012

    /** Primijeni hardkodiranu korekciju na zadanu cijenu (EUR/oz). */
    fun apply(valueEurPerOz: Double): Double {
        val fixed = FIXED_EUR
        val slope = PCT_SLOPE
        return (valueEurPerOz + fixed) * (1.0 + slope)
    }

    /** Ako želiš testirati druge vrijednosti bez mijenjanja konstanti. */
    fun apply(valueEurPerOz: Double, fixedEur: Double, pctSlope: Double): Double {
        return (valueEurPerOz + fixedEur) * (1.0 + pctSlope)
    }
}