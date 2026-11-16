package de.karoofun.karoo

import io.hammerhead.karooext.KarooSystemService
import java.util.Locale
import kotlin.math.abs

/** What the sky in the temperature cell shows on top of sun and moon. */
enum class Condition {
    CLEAR, PARTLY, OVERCAST, RAIN, STORM, SNOW, FOG;

    /** Grey sky: sun, moon and stars are hidden or faint. */
    val grey: Boolean get() = this != CLEAR && this != PARTLY

    val wet: Boolean get() = this == RAIN || this == STORM
}

/** Where the wind comes from relative to the riding direction. */
enum class WindSide { TAIL, HEAD, CROSS }

/** Current weather at the rider's position; wind in km/h, [windFrom] in degrees the wind blows from. */
data class Weather(val condition: Condition, val windKmh: Double, val windFrom: Double, val fetchedAt: Long) {

    fun freshAt(now: Long) = now - fetchedAt < MAX_AGE_MS

    /** Tailwind within 60° of the riding direction, headwind within 60° of the opposite, else crosswind. */
    fun windSide(bearing: Double?): WindSide {
        bearing ?: return WindSide.CROSS
        val towards = windFrom + 180
        val diff = abs(((towards - bearing) % 360 + 540) % 360 - 180)
        return when {
            diff <= 60 -> WindSide.TAIL
            diff >= 120 -> WindSide.HEAD
            else -> WindSide.CROSS
        }
    }

    companion object {
        const val MAX_AGE_MS = 45 * 60_000L
        const val MIN_WIND_KMH = 5.0

        /**
         * Reads the "current" block of an Open-Meteo forecast response. A tiny regex parser is enough
         * for four flat numbers and keeps the code testable without Android's org.json.
         */
        fun parse(json: String, fetchedAt: Long): Weather? {
            val current = Regex("\"current\"\\s*:\\s*\\{([^}]*)\\}").find(json)?.groupValues?.get(1) ?: return null
            fun number(key: String) = Regex("\"$key\"\\s*:\\s*(-?[0-9.]+)").find(current)?.groupValues?.get(1)?.toDoubleOrNull()
            val code = number("weather_code")?.toInt() ?: return null
            val clouds = number("cloud_cover") ?: 0.0
            return Weather(condition(code, clouds), number("wind_speed_10m") ?: 0.0, number("wind_direction_10m") ?: 0.0, fetchedAt)
        }

        /** WMO weather code to what we draw; plain clear/cloudy codes go by cloud cover in percent. */
        fun condition(code: Int, cloudCover: Double) = when (code) {
            in 95..99 -> Condition.STORM
            in 71..77, 85, 86 -> Condition.SNOW
            in 51..67, in 80..82 -> Condition.RAIN
            45, 48 -> Condition.FOG
            else -> when {
                cloudCover < 25 -> Condition.CLEAR
                cloudCover < 75 -> Condition.PARTLY
                else -> Condition.OVERCAST
            }
        }

        fun url(latitude: Double, longitude: Double) = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f" +
                "&current=cloud_cover,weather_code,wind_speed_10m,wind_direction_10m&wind_speed_unit=kmh",
            latitude, longitude,
        )
    }
}

/** Current weather through the Karoo's connection; null without a connection, so the dashboard keeps the clear sky. */
suspend fun KarooSystemService.fetchWeather(latitude: Double, longitude: Double, now: Long): Weather? =
    httpGet(Weather.url(latitude, longitude))?.decodeToString()?.let { Weather.parse(it, now) }
