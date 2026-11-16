package de.karoofun.karoo

import java.util.Locale
import kotlin.math.roundToInt

/** Display units and number format. Thresholds (freezing, hot) always work on the °C base value. */
data class Units(
    val imperialDistance: Boolean = false,
    val imperialElevation: Boolean = false,
    val imperialTemperature: Boolean = false,
    val locale: Locale = Locale.getDefault(),
) {
    val speedUnit get() = if (imperialDistance) "mph" else "km/h"
    val distanceUnit get() = if (imperialDistance) "mi" else "km"
    val elevationUnit get() = if (imperialElevation) "ft" else "m"
    val temperatureUnit get() = if (imperialTemperature) "°F" else "°C"

    fun speed(metersPerSecond: Double) = decimal(if (imperialDistance) metersPerSecond * MPH_PER_MPS else metersPerSecond * KMH_PER_MPS, 1)

    fun distance(meters: Double) = decimal(distanceValue(meters), 2)

    fun distanceValue(meters: Double) = if (imperialDistance) meters / METERS_PER_MILE else meters / 1000

    fun shortDistance(meters: Double): String {
        val value = distanceValue(meters)
        return if (value < 10 && value != 0.0) "${decimal(value, 1)} $distanceUnit" else "${value.roundToInt()} $distanceUnit"
    }

    fun temperature(celsius: Double) = decimal(if (imperialTemperature) celsius * 9 / 5 + 32 else celsius, 1)

    fun elevation(meters: Double) = (if (imperialElevation) meters * FEET_PER_METER else meters).roundToInt().toString()

    fun grade(percent: Double) = (if (percent >= 0) "+" else "") + decimal(percent, 1)

    private fun decimal(value: Double, digits: Int) = String.format(locale, "%.${digits}f", value)

    private companion object {
        const val KMH_PER_MPS = 3.6
        const val MPH_PER_MPS = 2.236936
        const val FEET_PER_METER = 3.28084
        const val METERS_PER_MILE = 1609.344
    }
}

/**
 * Heart rate zone 1..5 from the rider's Karoo zones. Without configured zones, falls back to the usual
 * 60/70/80/90 % split of the max heart rate (assuming 175 when the profile has none).
 */
fun heartRateZone(heartRate: Double, zoneStarts: List<Int>, maxHr: Int): Int {
    val starts = zoneStarts.takeIf { it.size >= 2 }
        ?: listOf(0, 60, 70, 80, 90).map { percentOf(if (maxHr >= MIN_MAX_HR) maxHr else DEFAULT_MAX_HR, it) }
    val index = starts.indexOfLast { heartRate >= it }.coerceAtLeast(0)
    return (index + 1).coerceAtMost(5)
}

/** Rounded percentage in integer math, so 70 % of 175 is 123 and not 122 from 122.4999… */
fun percentOf(value: Int, percent: Int) = (value * percent + 50) / 100

private const val MIN_MAX_HR = 100
private const val DEFAULT_MAX_HR = 175
