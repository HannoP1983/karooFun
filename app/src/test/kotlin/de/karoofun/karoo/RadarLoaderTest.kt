package de.karoofun.karoo

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarLoaderTest {
    private val munich = 48.137 to 11.575
    private val berlin = 52.520 to 13.405

    private fun windowAt(position: Pair<Double, Double>) =
        RadarWindow.around(position.first, position.second, RadarStore.zoom.value, 478, 494)

    @Test
    fun firstFixStopsTheRoundForTheStalePosition() = runBlocking {
        // The Karoo reports an old position first; the real fix arrives while the first request runs
        var position = munich
        val requests = mutableListOf<String>()
        val loader = RadarLoader(478, 494, null) { url ->
            requests += url
            position = berlin
            delay(200)
            null
        }
        val job = launch { loader.run({ position }) }
        withTimeout(5_000) {
            while (requests.size < 2) delay(50)
        }
        job.cancel()
        val berlinBox = windowAt(berlin).bbox(0.0, 0.5)
        assertTrue(requests[1], requests[1].contains(berlinBox))
        assertEquals(windowAt(berlin), loader.state.value.window)
    }

    @Test
    fun aFixElsewhereDoesNotWaitForTheRetry() = runBlocking {
        // No connection at the stale position: the failed round must not hold back the real one
        var position = munich
        val loader = RadarLoader(478, 494, null) { null }
        val job = launch { loader.run({ position }) }
        withTimeout(3_000) {
            while (loader.state.value.window != windowAt(munich)) delay(50)
        }
        delay(1_500)
        position = berlin
        withTimeout(3_000) {
            while (loader.state.value.window != windowAt(berlin)) delay(50)
        }
        job.cancel()
    }
}
