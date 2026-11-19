package de.karoofun.karoo

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Made-up alpine rides, shown while the user edits ride pages and in the previews. */
object DemoRide {
    enum class Scenario(
        val speedKmh: Double, val averageKmh: Double, val temperature: Double, val heartRate: Double, val km: Double,
        val cadence: Double, val power: Double, val gear: Int,
    ) {
        COLD(21.4, 24.8, -3.2, 128.0, 12.37, 64.0, 162.0, 3),
        NORMAL(26.3, 25.2, 14.6, 151.0, 41.05, 88.0, 248.0, 7),
        HOT(32.6, 27.1, 31.7, 176.0, 68.42, 104.0, 312.0, 11),
        DESCENT(72.4, 27.8, 18.3, 141.0, 52.6, 0.0, 0.0, 1),
    }

    private val profiles = HashMap<Scenario, List<ProfilePoint>>()
    private const val ALPS_LATITUDE = 47.42
    private const val ALPS_LONGITUDE = 10.98

    /** The scenario [frame] seconds into the demo: distance keeps growing at the scenario speed. */
    fun snapshot(scenario: Scenario, frame: Int): RideSnapshot {
        val points = synchronized(profiles) { profiles.getOrPut(scenario) { profile(scenario.km) } }
        val grades = smoothedGrades(points)
        val ascent = points.zipWithNext().sumOf { (a, b) -> max(0.0, b.elevation - a.elevation) }
        return RideSnapshot(
            speed = scenario.speedKmh / 3.6,
            averageSpeed = scenario.averageKmh / 3.6,
            maxSpeed = max(scenario.speedKmh, 68.4) / 3.6,
            temperature = scenario.temperature,
            heartRate = scenario.heartRate,
            averageHeartRate = scenario.heartRate - 14,
            maxHeartRate = max(scenario.heartRate, 171.0),
            cadence = scenario.cadence,
            averageCadence = 84.0,
            maxCadence = max(scenario.cadence, 118.0),
            power = scenario.power,
            averagePower = 196.0,
            calories = 1240.0 + frame,
            distanceToTop = 2100.0,
            elevationToTop = 184.0,
            distanceToNextTurn = 240.0,
            gears = Gears(front = if (scenario == Scenario.COLD) 1 else 2, frontMax = 2, rear = scenario.gear, rearMax = 12),
            distance = scenario.km * 1000 + frame * scenario.speedKmh / 3.6,
            points = points,
            elevation = points.last().elevation,
            ascent = ascent,
            grade = if (scenario == Scenario.DESCENT) -8.7 else grades.last(),
            latitude = ALPS_LATITUDE,
            longitude = ALPS_LONGITUDE,
            bearing = 90.0,
        )
    }

    private fun profile(km: Double): List<ProfilePoint> {
        val n = max(40, (km * 7).roundToInt())
        val points = ArrayList<ProfilePoint>()
        var elevation = 380.0
        for (i in 0..n) {
            val d = i.toDouble() / n * km
            val target = 520 + 300 * sin(d / 6.0) + 160 * sin(d / 2.3 + 1) +
                260 * max(0.0, sin(d / 3.4 - 2)) + if (d > 44) 420 * min(1.0, (d - 44) / 11) else 0.0
            elevation += (target - elevation) * 0.12
            points += ProfilePoint(d * 1000, elevation)
        }
        return points
    }
}
