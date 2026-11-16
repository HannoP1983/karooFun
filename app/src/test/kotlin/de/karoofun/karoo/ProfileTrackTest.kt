package de.karoofun.karoo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTrackTest {
    @Test
    fun `stores a point only every step meters`() {
        val track = ProfileTrack(minStep = 20.0)
        assertTrue(track.add(0.0, 100.0))
        assertFalse(track.add(10.0, 101.0))
        assertTrue(track.add(25.0, 102.0))
        assertEquals(listOf(0.0, 25.0), track.snapshot().map { it.distance })
    }

    @Test
    fun `distance jumping back starts a new profile`() {
        val track = ProfileTrack()
        track.add(0.0, 100.0)
        track.add(5_000.0, 300.0)
        assertTrue(track.add(0.0, 120.0))
        assertEquals(listOf(ProfilePoint(0.0, 120.0)), track.snapshot())
    }

    @Test
    fun `long rides are thinned but keep start and latest point`() {
        val track = ProfileTrack(minStep = 10.0, maxPoints = 100)
        for (i in 0..10_000) track.add(i * 10.0, i.toDouble())
        val points = track.snapshot()
        assertTrue(points.size <= 100)
        assertEquals(0.0, points.first().distance, 0.0)
        assertTrue(points.last().distance >= 99_000.0)
        val gaps = points.zipWithNext { a, b -> b.distance - a.distance }.dropLast(1)
        assertEquals(1, gaps.distinct().size)
    }

    @Test
    fun `smoothed grade of a constant climb`() {
        val points = (0..50).map { ProfilePoint(it * 20.0, it * 20.0 * 0.08) }
        val grades = smoothedGrades(points)
        grades.forEach { assertEquals(8.0, it, 1e-9) }
    }

    @Test
    fun `smoothed grade when points are further apart than the window`() {
        val points = (0..20).map { ProfilePoint(it * 500.0, it * 500.0 * 0.06) }
        val grades = smoothedGrades(points, window = 200.0)
        grades.forEach { assertEquals(6.0, it, 1e-9) }
    }

    @Test
    fun `grade bands`() {
        assertEquals(GradeBand.DOWN, GradeBand.of(-0.1))
        assertEquals(GradeBand.FLAT, GradeBand.of(0.0))
        assertEquals(GradeBand.HARD, GradeBand.of(7.5))
        assertEquals(GradeBand.WALL, GradeBand.of(18.0))
    }
}
