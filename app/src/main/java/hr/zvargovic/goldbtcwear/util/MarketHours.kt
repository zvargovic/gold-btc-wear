package hr.zvargovic.goldbtcwear.util

import java.time.*

data class MarketStatus(val isOpen: Boolean, val nextChange: Instant)

object MarketHours {
    private val NY = ZoneId.of("America/New_York")
    private val PAUSE_START = LocalTime.of(17, 0) // 17:00 ET
    private val PAUSE_END   = LocalTime.of(18, 0) // 18:00 ET

    /** CME Globex (metals): daily pause 17:00–18:00 ET, weekend Fri 17:00 → Sun 18:00 ET */
    fun status(now: Instant = Instant.now()): MarketStatus {
        val z = ZonedDateTime.ofInstant(now, NY)
        val dow = z.dayOfWeek
        val t   = z.toLocalTime()

        val isDailyPause = dow in DayOfWeek.MONDAY..DayOfWeek.THURSDAY &&
                t >= PAUSE_START && t < PAUSE_END

        val isWeekendClosed =
            (dow == DayOfWeek.FRIDAY   && t >= PAUSE_START) ||
                    dow == DayOfWeek.SATURDAY ||
                    (dow == DayOfWeek.SUNDAY   && t <  PAUSE_END)

        val open = !(isDailyPause || isWeekendClosed)
        val next = when {
            open && dow in DayOfWeek.MONDAY..DayOfWeek.THURSDAY && t < PAUSE_START ->
                z.withHour(17).withMinute(0).withSecond(0).withNano(0).toInstant()
            open && dow == DayOfWeek.FRIDAY && t < PAUSE_START ->
                z.withHour(17).withMinute(0).withSecond(0).withNano(0).toInstant()
            !open && isDailyPause ->
                z.withHour(18).withMinute(0).withSecond(0).withNano(0).toInstant()
            !open && isWeekendClosed -> {
                // sljedeća nedjelja 18:00 ET
                var n = z
                while (n.dayOfWeek != DayOfWeek.SUNDAY) n = n.plusDays(1)
                n.withHour(18).withMinute(0).withSecond(0).withNano(0).toInstant()
            }
            else -> z.plusMinutes(30).toInstant()
        }
        return MarketStatus(open, next)
    }
}