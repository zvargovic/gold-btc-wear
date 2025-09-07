package hr.zvargovic.goldbtcwear.util

import java.util.Calendar
import java.util.TimeZone

/**
 * Pojednostavljena COMEX/FX logika:
 *  - Otvoreno od nedjelje 23:00 UTC do petka 22:00 UTC.
 *  - Svaki dan tehnička pauza 22:00–23:00 UTC.
 *  - Subota uvijek zatvoreno.
 *
 * Ovo je dovoljno za throttle; po potrebi kasnije rafiniramo (praznici itd.).
 */
object MarketUtils {

    fun isMarketOpenUtc(nowMs: Long): Boolean {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = nowMs
        val dow = cal.get(Calendar.DAY_OF_WEEK) // 1=Sunday .. 7=Saturday
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        // Subota zatvoreno
        if (dow == Calendar.SATURDAY) return false

        // Dnevna pauza 22:00–23:00 UTC
        if (hour == 22) return false

        // Nedjelja: otvoreno tek od 23:00
        if (dow == Calendar.SUNDAY) return hour >= 23

        // Petak: zatvara se u 22:00 (ali taj sat je pauza, već gore vraća false)
        if (dow == Calendar.FRIDAY) return hour < 22

        // Ponedjeljak–Četvrtak (bez pauze 22–23)
        return true
    }
}