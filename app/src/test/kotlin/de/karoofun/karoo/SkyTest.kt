package de.karoofun.karoo

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SkyTest {
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun sunIsHighAtNoonInMunichOnMidsummer() {
        assertEquals(65.3, Astronomy.sunElevation(48.14, 11.58, at("2026-06-21T11:14:00Z")), 1.0)
        assertEquals(SkyPhase.DAY, Sky.at(48.14, 11.58, at("2026-06-21T11:14:00Z")).phase)
    }

    @Test
    fun sunIsFarBelowHorizonAtMidnight() {
        assertEquals(-18.4, Astronomy.sunElevation(48.14, 11.58, at("2026-06-20T23:14:00Z")), 1.0)
        assertEquals(SkyPhase.NIGHT, Sky.at(48.14, 11.58, at("2026-12-21T23:00:00Z")).phase)
    }

    @Test
    fun winterEveningGoesFromGoldenToDusk() {
        // Sunset in Munich on Dec 21 is about 15:22 UTC
        assertEquals(SkyPhase.GOLDEN, Sky.at(48.14, 11.58, at("2026-12-21T15:15:00Z")).phase)
        assertEquals(SkyPhase.DUSK, Sky.at(48.14, 11.58, at("2026-12-21T15:50:00Z")).phase)
    }

    @Test
    fun moonPhaseMatchesKnownNewAndFullMoon() {
        val newMoon = Astronomy.moonPhase(at("2024-01-11T11:57:00Z"))
        assertEquals(0.0, minOf(newMoon, 1 - newMoon), 0.03)
        assertEquals(0.5, Astronomy.moonPhase(at("2024-01-25T17:54:00Z")), 0.03)
    }

    @Test
    fun southernHemisphereIsMarked() {
        assertEquals(true, Sky.at(-33.9, 18.4, at("2026-06-21T12:00:00Z")).southern)
    }
}
