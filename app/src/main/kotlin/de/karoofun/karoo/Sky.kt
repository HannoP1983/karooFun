package de.karoofun.karoo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** How the temperature cell sky looks, from high sun down to night. */
enum class SkyPhase { DAY, LOW_SUN, GOLDEN, DUSK, NIGHT }

/** Coarse time of day for tinting weather; without a GPS fix it is day. */
enum class Daylight { DAY, EVENING, NIGHT }

val Sky?.daylight: Daylight
    get() = when (this?.phase) {
        SkyPhase.NIGHT -> Daylight.NIGHT
        SkyPhase.GOLDEN, SkyPhase.DUSK -> Daylight.EVENING
        else -> Daylight.DAY
    }

/**
 * Sun and moon as seen from the rider's position: sun elevation in degrees above the horizon,
 * moon phase from 0 (new) over 0.5 (full) back to 1 (new). [southern] mirrors the moon's lit side.
 */
data class Sky(val sunElevation: Double, val moonPhase: Double, val southern: Boolean) {
    val phase: SkyPhase
        get() = when {
            sunElevation >= HIGH_SUN -> SkyPhase.DAY
            sunElevation >= GOLDEN_HOUR -> SkyPhase.LOW_SUN
            sunElevation >= SUNSET -> SkyPhase.GOLDEN
            sunElevation >= NAUTICAL_DUSK -> SkyPhase.DUSK
            else -> SkyPhase.NIGHT
        }

    val dark: Boolean get() = phase == SkyPhase.DUSK || phase == SkyPhase.NIGHT

    /** No moon drawn a day or so around new moon. */
    val moonVisible: Boolean get() = moonPhase in NEW_MOON..(1 - NEW_MOON)

    companion object {
        const val HIGH_SUN = 20.0
        const val GOLDEN_HOUR = 6.0
        const val SUNSET = -1.0
        const val NAUTICAL_DUSK = -8.0
        private const val NEW_MOON = 0.03

        fun at(latitude: Double, longitude: Double, millis: Long) =
            Sky(Astronomy.sunElevation(latitude, longitude, millis), Astronomy.moonPhase(millis), latitude < 0)
    }
}

/** Low-precision sun and moon formulas (about 1° and a few hours off), plenty for drawing a sky. */
object Astronomy {
    private const val J2000 = 2451545.0
    private const val SYNODIC_MONTH = 29.530588853
    private const val NEW_MOON_JD = 2451550.1

    private fun julianDay(millis: Long) = millis / 86_400_000.0 + 2440587.5

    fun sunElevation(latitude: Double, longitude: Double, millis: Long): Double {
        val d = julianDay(millis) - J2000
        val g = Math.toRadians(357.529 + 0.98560028 * d)
        val l = Math.toRadians(280.459 + 0.98564736 * d + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val e = Math.toRadians(23.439 - 0.00000036 * d)
        val ra = atan2(cos(e) * sin(l), cos(l))
        val dec = asin(sin(e) * sin(l))
        val hourAngle = Math.toRadians(280.46061837 + 360.98564736629 * d + longitude) - ra
        val lat = Math.toRadians(latitude)
        return Math.toDegrees(asin(sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(hourAngle)))
    }

    fun moonPhase(millis: Long): Double {
        val cycles = (julianDay(millis) - NEW_MOON_JD) / SYNODIC_MONTH
        return cycles - floor(cycles)
    }
}
