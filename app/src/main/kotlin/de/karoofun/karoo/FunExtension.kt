package de.karoofun.karoo

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class FunExtension : KarooExtension("karoo-fun", "1") {
    private val karooSystem by lazy { KarooSystemService(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recording: Job? = null

    override val types by lazy {
        listOf(DashboardDataType(karooSystem, extension), RadarDataType(karooSystem, extension)) +
            ComicField.entries.map { FieldDataType(karooSystem, extension, it) { FunSettings.load(this) } }
    }

    override fun onCreate() {
        super.onCreate()
        karooSystem.connect { connected ->
            if (connected) startRecording()
        }
    }

    private fun startRecording() {
        recording?.cancel()
        recording = scope.launch { record() }
    }

    private fun CoroutineScope.record() {
        launch {
            combine(
                karooSystem.valueFlow(DataType.Type.DISTANCE),
                karooSystem.valueFlow(DataType.Type.PRESSURE_ELEVATION_CORRECTION),
            ) { distance, elevation -> distance to elevation }
                .collect { (distance, elevation) -> RideStore.onPosition(distance, elevation) }
        }
        val values = mapOf(
            DataType.Type.DISTANCE to RideStore::onDistance,
            DataType.Type.SPEED to RideStore::onSpeed,
            DataType.Type.AVERAGE_SPEED to RideStore::onAverageSpeed,
            DataType.Type.MAX_SPEED to RideStore::onMaxSpeed,
            DataType.Type.TEMPERATURE to RideStore::onTemperature,
            DataType.Type.HEART_RATE to RideStore::onHeartRate,
            DataType.Type.AVERAGE_HR to RideStore::onAverageHeartRate,
            DataType.Type.MAX_HR to RideStore::onMaxHeartRate,
            DataType.Type.CADENCE to RideStore::onCadence,
            DataType.Type.AVERAGE_CADENCE to RideStore::onAverageCadence,
            DataType.Type.MAX_CADENCE to RideStore::onMaxCadence,
            DataType.Type.ELEVATION_GAIN to RideStore::onAscent,
            DataType.Type.ELEVATION_GRADE to RideStore::onGrade,
        )
        for ((type, update) in values) {
            launch { karooSystem.valueFlow(type).collect { update(it) } }
        }
        // Values that may end (no power meter, climb over, route done) clear instead of keeping the last one
        val optional = mapOf(
            DataType.Type.SMOOTHED_3S_AVERAGE_POWER to RideStore::onPower,
            DataType.Type.AVERAGE_POWER to RideStore::onAveragePower,
            DataType.Type.CALORIES to RideStore::onCalories,
            DataType.Type.DISTANCE_TO_TOP to RideStore::onDistanceToTop,
            DataType.Type.ELEVATION_TO_TOP to RideStore::onElevationToTop,
            DataType.Type.DISTANCE_TO_NEXT_TURN to RideStore::onDistanceToNextTurn,
        )
        for ((type, update) in optional) {
            launch { karooSystem.optionalValueFlow(type).collect { update(it) } }
        }
        launch {
            karooSystem.fieldsFlow(DataType.Type.SHIFTING_FRONT_GEAR).collect { values ->
                RideStore.onFrontGear(values?.get(DataType.Field.SHIFTING_FRONT_GEAR)?.toInt(),
                    values?.get(DataType.Field.SHIFTING_FRONT_GEAR_MAX)?.toInt(), values?.get(DataType.Field.SHIFTING_FRONT_GEAR_TEETH)?.toInt())
            }
        }
        launch {
            karooSystem.fieldsFlow(DataType.Type.SHIFTING_REAR_GEAR).collect { values ->
                RideStore.onRearGear(values?.get(DataType.Field.SHIFTING_REAR_GEAR)?.toInt(),
                    values?.get(DataType.Field.SHIFTING_REAR_GEAR_MAX)?.toInt(), values?.get(DataType.Field.SHIFTING_REAR_GEAR_TEETH)?.toInt())
            }
        }
        launch {
            karooSystem.streamDataFlow(DataType.Type.LOCATION).collect { state ->
                val values = (state as? StreamState.Streaming)?.dataPoint?.values ?: return@collect
                val latitude = values[DataType.Field.LOC_LATITUDE] ?: return@collect
                val longitude = values[DataType.Field.LOC_LONGITUDE] ?: return@collect
                RideStore.onLocation(latitude, longitude, values[DataType.Field.LOC_BEARING])
            }
        }
        launch { pollWeather() }
        RadarStore.startIfUsed(this@FunExtension, this, { karooSystem.httpGet(it) }, RadarDataType::riderPosition)
        launch { watchForTakeovers() }
    }

    /** Current weather every 15 minutes once there is a GPS fix; without a connection the last one simply ages out. */
    private suspend fun CoroutineScope.pollWeather() {
        while (isActive) {
            val data = RideStore.state.value
            if (data.latitude == null || data.longitude == null) {
                delay(10_000)
                continue
            }
            karooSystem.fetchWeather(data.latitude, data.longitude, System.currentTimeMillis())?.let(RideStore::onWeather)
            delay(WEATHER_INTERVAL_MS)
        }
    }

    /**
     * Checks the takeover triggers once per second, also while the page is hidden, so conditions like
     * "20 minutes in zone 2-3" keep counting. Takeovers themselves only fire while the page is visible.
     */
    private suspend fun CoroutineScope.watchForTakeovers() {
        val rider = karooSystem.rider()
        var settings = FunSettings.load(this@FunExtension)
        val detector = EventDetector { settings }
        val sayings = resources.getStringArray(R.array.pokal_sayings).size
        while (isActive) {
            settings = FunSettings.load(this@FunExtension)
            val data = RideStore.state.value
            val now = System.currentTimeMillis()
            val zone = rider.zone(data.heartRate, settings)
            val sample = TriggerSample(now, data.speed?.times(KMH_PER_MPS), data.averageSpeed?.times(KMH_PER_MPS), data.grade, zone)
            val steady = detector.partyMinutes(now)
            val fired = detector.onSample(sample, visible = RideStore.visibleViews.get() > 0)
            if (fired != null) {
                // One saying per takeover, so it stays readable the whole time
                RideStore.fire(ActiveTakeover(fired, Random.nextInt(sayings), now, zone, steady))
            }
            delay(1_000)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        karooSystem.disconnect()
        super.onDestroy()
    }

    private companion object {
        const val KMH_PER_MPS = 3.6
        const val WEATHER_INTERVAL_MS = 15 * 60_000L
    }
}
