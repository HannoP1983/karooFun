package de.karoofun.karoo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    private val response = """
        {"latitude":47.42,"longitude":10.98,"current_units":{"time":"iso8601","interval":"seconds","cloud_cover":"%",
        "weather_code":"wmo code","wind_speed_10m":"km/h","wind_direction_10m":"°"},
        "current":{"time":"2026-11-16T19:45","interval":900,"cloud_cover":100,"weather_code":61,"wind_speed_10m":23.4,"wind_direction_10m":250}}
    """.trimIndent()

    @Test
    fun parsesCurrentBlockAndIgnoresUnits() {
        val weather = Weather.parse(response, 1000L)!!
        assertEquals(Condition.RAIN, weather.condition)
        assertEquals(23.4, weather.windKmh, 0.01)
        assertEquals(250.0, weather.windFrom, 0.01)
        assertEquals(1000L, weather.fetchedAt)
    }

    @Test
    fun brokenResponseGivesNull() {
        assertNull(Weather.parse("{\"error\":true}", 0L))
    }

    @Test
    fun mapsWeatherCodes() {
        assertEquals(Condition.STORM, Weather.condition(95, 100.0))
        assertEquals(Condition.SNOW, Weather.condition(73, 100.0))
        assertEquals(Condition.RAIN, Weather.condition(81, 100.0))
        assertEquals(Condition.FOG, Weather.condition(45, 100.0))
        assertEquals(Condition.CLEAR, Weather.condition(0, 10.0))
        assertEquals(Condition.PARTLY, Weather.condition(2, 50.0))
        assertEquals(Condition.OVERCAST, Weather.condition(3, 95.0))
    }

    @Test
    fun windSideFollowsRidingDirection() {
        // Riding east (90°): wind from the west pushes, wind from the east brakes
        assertEquals(WindSide.TAIL, Weather(Condition.CLEAR, 20.0, 270.0, 0).windSide(90.0))
        assertEquals(WindSide.HEAD, Weather(Condition.CLEAR, 20.0, 90.0, 0).windSide(90.0))
        assertEquals(WindSide.CROSS, Weather(Condition.CLEAR, 20.0, 0.0, 0).windSide(90.0))
        assertEquals(WindSide.HEAD, Weather(Condition.CLEAR, 20.0, 350.0, 0).windSide(10.0))
        assertEquals(WindSide.CROSS, Weather(Condition.CLEAR, 20.0, 350.0, 0).windSide(null))
    }

    @Test
    fun weatherGoesStaleAfter45Minutes() {
        val weather = Weather(Condition.CLEAR, 0.0, 0.0, 0)
        assertTrue(weather.freshAt(44 * 60_000L))
        assertFalse(weather.freshAt(46 * 60_000L))
    }
}
