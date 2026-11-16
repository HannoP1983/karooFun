package de.karoofun.karoo

import kotlin.math.max
import kotlin.math.min

/** A single sample of the ridden profile: distance from ride start and elevation, both in meters. */
data class ProfilePoint(val distance: Double, val elevation: Double)

/**
 * Collects the ridden elevation profile with bounded memory.
 *
 * A point is stored every [step] meters. When more than [maxPoints] are collected, every other point
 * is dropped and the step doubles, so even very long rides keep an evenly spaced profile.
 */
class ProfileTrack(
    private val minStep: Double = 20.0,
    private val maxPoints: Int = 1200,
) {
    private val points = ArrayList<ProfilePoint>()
    private var step = minStep

    /** Returns true if the profile changed. A distance that jumps back means a new ride started. */
    @Synchronized
    fun add(distance: Double, elevation: Double): Boolean {
        val last = points.lastOrNull()
        if (last != null && distance < last.distance - RESET_TOLERANCE) {
            reset()
        } else if (last != null && distance - last.distance < step) {
            return false
        }
        points += ProfilePoint(distance, elevation)
        if (points.size > maxPoints) thin()
        return true
    }

    @Synchronized
    fun reset() {
        points.clear()
        step = minStep
    }

    @Synchronized
    fun snapshot(): List<ProfilePoint> = points.toList()

    private fun thin() {
        val last = points.last()
        val kept = points.filterIndexed { i, _ -> i % 2 == 0 }.toMutableList()
        if (kept.last() != last) kept += last
        points.clear()
        points.addAll(kept)
        step *= 2
    }

    private companion object {
        const val RESET_TOLERANCE = 50.0
    }
}

/**
 * Grade in percent for every point, measured over a window of [window] meters centered on the point.
 * Smoothing keeps barometric noise from turning the colored profile into confetti.
 */
fun smoothedGrades(points: List<ProfilePoint>, window: Double = 200.0): DoubleArray {
    val grades = DoubleArray(points.size)
    val half = window / 2
    var lo = 0
    var hi = 0
    for (i in points.indices) {
        val d = points[i].distance
        while (points[lo].distance < d - half) lo++
        while (hi + 1 < points.size && points[hi + 1].distance <= d + half) hi++
        // Sparse (thinned) profiles have points further apart than the window: always span the neighbors
        val from = min(lo, max(i - 1, 0))
        val to = max(hi, min(i + 1, points.lastIndex))
        val run = points[to].distance - points[from].distance
        grades[i] = if (run > 0) (points[to].elevation - points[from].elevation) / run * 100 else 0.0
    }
    return grades
}
