package de.karoofun.karoo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

/**
 * Everything the dashboard needs to draw one frame, in Karoo base units:
 * speeds in m/s, distances and elevations in meters, temperature in °C, grade in percent, position and bearing in degrees.
 */
data class RideSnapshot(
    val speed: Double? = null,
    val averageSpeed: Double? = null,
    val maxSpeed: Double? = null,
    val temperature: Double? = null,
    val heartRate: Double? = null,
    val averageHeartRate: Double? = null,
    val maxHeartRate: Double? = null,
    val cadence: Double? = null,
    val averageCadence: Double? = null,
    val maxCadence: Double? = null,
    val power: Double? = null,
    val averagePower: Double? = null,
    val calories: Double? = null,
    /** Work done in the ride, in kJ. */
    val energy: Double? = null,
    val distanceToTop: Double? = null,
    val elevationToTop: Double? = null,
    /** Height of the current climb, see [ClimbTracker]. */
    val climbTotal: Double? = null,
    /** When the last real climb was topped out, and how high it was. */
    val summitAt: Long? = null,
    val summitHeight: Double? = null,
    val distanceToNextTurn: Double? = null,
    /** Meters back to the route while the rider is off it, null on the route or without one. */
    val offRoute: Double? = null,
    val gears: Gears = Gears(),
    /** When front or rear gear last changed. */
    val gearSince: Long? = null,
    val distance: Double? = null,
    val points: List<ProfilePoint> = emptyList(),
    val elevation: Double? = null,
    val ascent: Double? = null,
    val grade: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val bearing: Double? = null,
    /** How exact the position is, in meters; null if the Karoo doesn't say. */
    val accuracy: Double? = null,
    val weather: Weather? = null,
) {
    /**
     * A real GPS fix. Before one the Karoo may report a coarse or old position, and loading radar there
     * only holds up loading at the real one.
     */
    val hasFix get() = latitude != null && longitude != null && (accuracy == null || accuracy <= FIX_ACCURACY)

    private companion object {
        const val FIX_ACCURACY = 50.0
    }
}

/**
 * Electronic shifting as the Karoo reports it: gear numbers count from 1, front 1 is the small ring and rear 1
 * the biggest cog. Max is the number of rings or cogs, teeth belong to the gear in use. Null without shifting.
 */
data class Gears(
    val front: Int? = null,
    val frontMax: Int? = null,
    val frontTeeth: Int? = null,
    val rear: Int? = null,
    val rearMax: Int? = null,
    val rearTeeth: Int? = null,
)

/**
 * A takeover that fired and waits to be shown. [variant] picks the saying, [steadyMinutes] feeds the party panel,
 * [boilSeconds] the boiling one.
 */
data class ActiveTakeover(
    val type: Takeover,
    val variant: Int,
    val firedAt: Long,
    val zone: Int?,
    val steadyMinutes: Int,
    val boilSeconds: Int = 0,
)

/**
 * Process-wide ride state. Recording happens in the extension service so the profile keeps growing
 * while the page with the dashboard is not visible.
 */
object RideStore {
    private val track = ProfileTrack()
    private val climbs = ClimbTracker()
    private val _state = MutableStateFlow(RideSnapshot())
    val state: StateFlow<RideSnapshot> = _state.asStateFlow()

    private val _takeover = MutableStateFlow<ActiveTakeover?>(null)
    val takeover: StateFlow<ActiveTakeover?> = _takeover.asStateFlow()

    /** Number of dashboard views on screen; takeovers only fire while someone can see them. */
    val visibleViews = AtomicInteger()

    fun fire(takeover: ActiveTakeover) {
        _takeover.value = takeover
    }

    fun done(takeover: ActiveTakeover) {
        _takeover.compareAndSet(takeover, null)
    }

    fun onPosition(distance: Double, elevation: Double) {
        val changed = track.add(distance, elevation)
        _state.update {
            if (changed) it.copy(points = track.snapshot(), elevation = elevation) else it.copy(elevation = elevation)
        }
    }

    fun onDistance(distance: Double) = _state.update { it.copy(distance = distance) }

    fun onSpeed(speed: Double) = _state.update { it.copy(speed = speed) }

    fun onAverageSpeed(speed: Double) = _state.update { it.copy(averageSpeed = speed) }

    fun onMaxSpeed(speed: Double) = _state.update { it.copy(maxSpeed = speed) }

    fun onTemperature(temperature: Double) = _state.update { it.copy(temperature = temperature) }

    fun onHeartRate(heartRate: Double) = _state.update { it.copy(heartRate = heartRate) }

    fun onAverageHeartRate(heartRate: Double) = _state.update { it.copy(averageHeartRate = heartRate) }

    fun onMaxHeartRate(heartRate: Double) = _state.update { it.copy(maxHeartRate = heartRate) }

    fun onCadence(cadence: Double) = _state.update { it.copy(cadence = cadence) }

    fun onAverageCadence(cadence: Double) = _state.update { it.copy(averageCadence = cadence) }

    fun onMaxCadence(cadence: Double) = _state.update { it.copy(maxCadence = cadence) }

    fun onPower(power: Double?) = _state.update { it.copy(power = power) }

    fun onAveragePower(power: Double?) = _state.update { it.copy(averagePower = power) }

    fun onCalories(calories: Double?) = _state.update { it.copy(calories = calories) }

    fun onEnergy(kiloJoules: Double?) = _state.update { it.copy(energy = kiloJoules) }

    fun onDistanceToTop(meters: Double?) = _state.update { it.copy(distanceToTop = meters) }

    fun onElevationToTop(meters: Double?, now: Long = System.currentTimeMillis()) = synchronized(climbs) {
        climbs.onElevationToTop(meters, now)
        _state.update {
            it.copy(elevationToTop = meters, climbTotal = climbs.total, summitAt = climbs.summitAt, summitHeight = climbs.summitHeight)
        }
    }

    fun onDistanceToNextTurn(meters: Double?) = _state.update { it.copy(distanceToNextTurn = meters) }

    fun onOffRoute(meters: Double?) = _state.update { it.copy(offRoute = meters) }

    fun onFrontGear(gear: Int?, max: Int?, teeth: Int?, now: Long = System.currentTimeMillis()) = _state.update {
        val since = if (gear != null && gear != it.gears.front) now else it.gearSince
        it.copy(gears = it.gears.copy(front = gear, frontMax = max ?: it.gears.frontMax, frontTeeth = teeth), gearSince = since)
    }

    fun onRearGear(gear: Int?, max: Int?, teeth: Int?, now: Long = System.currentTimeMillis()) = _state.update {
        val since = if (gear != null && gear != it.gears.rear) now else it.gearSince
        it.copy(gears = it.gears.copy(rear = gear, rearMax = max ?: it.gears.rearMax, rearTeeth = teeth), gearSince = since)
    }

    fun onAscent(ascent: Double) = _state.update { it.copy(ascent = ascent) }

    fun onGrade(grade: Double) = _state.update { it.copy(grade = grade) }

    fun onLocation(latitude: Double, longitude: Double, bearing: Double?, accuracy: Double?) = _state.update {
        it.copy(latitude = latitude, longitude = longitude, bearing = bearing ?: it.bearing, accuracy = accuracy)
    }

    fun onWeather(weather: Weather) = _state.update { it.copy(weather = weather) }
}
