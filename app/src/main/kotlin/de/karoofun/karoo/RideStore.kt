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
    val distanceToTop: Double? = null,
    val elevationToTop: Double? = null,
    val distanceToNextTurn: Double? = null,
    val gears: Gears = Gears(),
    val distance: Double? = null,
    val points: List<ProfilePoint> = emptyList(),
    val elevation: Double? = null,
    val ascent: Double? = null,
    val grade: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val bearing: Double? = null,
    val weather: Weather? = null,
)

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

/** A takeover that fired and waits to be shown. [variant] picks the saying, [steadyMinutes] feeds the party panel. */
data class ActiveTakeover(
    val type: Takeover,
    val variant: Int,
    val firedAt: Long,
    val zone: Int?,
    val steadyMinutes: Int,
)

/**
 * Process-wide ride state. Recording happens in the extension service so the profile keeps growing
 * while the page with the dashboard is not visible.
 */
object RideStore {
    private val track = ProfileTrack()
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

    fun onDistanceToTop(meters: Double?) = _state.update { it.copy(distanceToTop = meters) }

    fun onElevationToTop(meters: Double?) = _state.update { it.copy(elevationToTop = meters) }

    fun onDistanceToNextTurn(meters: Double?) = _state.update { it.copy(distanceToNextTurn = meters) }

    fun onFrontGear(gear: Int?, max: Int?, teeth: Int?) = _state.update {
        it.copy(gears = it.gears.copy(front = gear, frontMax = max ?: it.gears.frontMax, frontTeeth = teeth))
    }

    fun onRearGear(gear: Int?, max: Int?, teeth: Int?) = _state.update {
        it.copy(gears = it.gears.copy(rear = gear, rearMax = max ?: it.gears.rearMax, rearTeeth = teeth))
    }

    fun onAscent(ascent: Double) = _state.update { it.copy(ascent = ascent) }

    fun onGrade(grade: Double) = _state.update { it.copy(grade = grade) }

    fun onLocation(latitude: Double, longitude: Double, bearing: Double?) = _state.update {
        it.copy(latitude = latitude, longitude = longitude, bearing = bearing ?: it.bearing)
    }

    fun onWeather(weather: Weather) = _state.update { it.copy(weather = weather) }
}
