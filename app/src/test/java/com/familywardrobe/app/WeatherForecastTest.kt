package com.familywardrobe.app

import org.junit.Assert.*
import org.junit.Test

/** WTH-01: the forecast's temperature to dress for, and the mode as its band. */
class WeatherForecastTest {
    private val today = "2026-11-12"
    private fun forecast(age: Long = 0) = Forecast(WeatherCity("City", 50.0, 20.0), System.currentTimeMillis() - age, today, 7.0,
        listOf(ForecastDay(today, 5.0, 20.0, 10), ForecastDay("2026-11-13", 2.0, 8.0, null), ForecastDay("2026-11-14", 16.0, 28.0, 80)))

    @Test fun todayIsTheDayAndHowItFeelsNow() {
        val f = forecast()
        // A cold morning (+7 now) on a day up to +20: +11 to dress for — «прохолодно», not «холодно».
        assertEquals(11.0, f.temperature(today)!!, 1e-9); assertEquals("cool", f.mode(today)); assertEquals("cold", RoadmapRules.weatherMode(f.feelsLike))
        // Other days: the high counts twice.
        assertEquals(6.0, f.temperature("2026-11-13")!!, 1e-9); assertEquals("cold", f.mode("2026-11-13"))
        assertEquals(24.0, f.temperature("2026-11-14")!!, 1e-9); assertEquals("warm", f.mode("2026-11-14"))
        assertNull(f.temperature("2026-11-20")); assertNull(f.mode("2026-11-20"))
        // Today missing from the days: how it feels now.
        assertEquals(7.0, f.copy(days = f.days.drop(1)).temperature(today)!!, 1e-9)
    }

    /** WTH-02: the chance of rain makes a mild day «Дощ»; it follows the same freshness as the temperature. */
    @Test fun rainFromTheForecast() {
        val wet = forecast().let { f -> f.copy(days = f.days.map { if (it.date == today) it.copy(rain = 70) else it }) }
        assertEquals(70, wet.rain(today)); assertEquals("rain", wet.mode(today))
        assertEquals("cool", forecast().mode(today)); assertEquals(10, forecast().rain(today))
        // +24 with 80%: still «тепло»; a day not in the forecast has no chance of rain.
        assertEquals("warm", wet.mode("2026-11-14")); assertNull(wet.rain("2026-11-20"))
        // An old forecast is not used for rain either.
        assertNull(wet.copy(fetchedAt = System.currentTimeMillis() - 25 * 60 * 60 * 1000L).rain(today))
    }

    @Test fun anOldForecastIsNotUsed() {
        // Older than six hours: "now" is no longer now, today is a day like the others. Older than a day: nothing.
        assertEquals(15.0, forecast(7 * 60 * 60 * 1000L).temperature(today)!!, 1e-9)
        assertNull(forecast(25 * 60 * 60 * 1000L).temperature(today)); assertNull(forecast(25 * 60 * 60 * 1000L).mode("2026-11-13"))
    }
}
