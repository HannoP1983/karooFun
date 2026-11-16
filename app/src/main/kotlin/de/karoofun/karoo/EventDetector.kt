package de.karoofun.karoo

/** The full-page comic actions, in priority order. */
enum class Takeover { POKAL, FIRE, PARTY }

/** One sample per second of what the triggers look at. Speeds in km/h, grade in percent. */
data class TriggerSample(
    val timeMillis: Long,
    val speedKmh: Double?,
    val averageKmh: Double?,
    val grade: Double?,
    val zone: Int?,
)

/**
 * Decides when a takeover fires. Conditions that must hold for a while ("for 10 s", "for 20 min") are
 * tracked continuously; one gap resets them. After any takeover all of them pause for the cooldown, so the
 * actions stay special.
 */
class EventDetector(private val settings: () -> FunSettings) {
    private var fireSince: Long? = null
    private var partySince: Long? = null
    private var lastFired: Long? = null

    /** Feed one sample; returns the takeover to show now, if any. Only fires while [visible]. */
    fun onSample(sample: TriggerSample, visible: Boolean): Takeover? {
        val s = settings()
        val t = sample.timeMillis
        val speed = sample.speedKmh
        val average = sample.averageKmh?.takeIf { it > 0 }
        val zone = sample.zone

        fireSince = if (speed != null && average != null && zone != null &&
            speed >= average * (1 + s.fireAbovePercent / 100.0) && zone >= s.fireMinZone
        ) fireSince ?: t else null

        partySince = if (zone != null && zone in s.partyMinZone..s.partyMaxZone && (speed ?: 0.0) > 0) partySince ?: t else null

        if (!visible) return null
        val last = lastFired
        if (last != null && t - last < s.cooldownMinutes * MINUTE) return null

        val pokal = s.pokalEnabled && speed != null && sample.grade != null &&
            speed >= s.pokalSpeedKmh && sample.grade <= s.pokalGrade && sample.grade < 0
        val fire = s.fireEnabled && fireSince.let { it != null && t - it >= s.fireSeconds * SECOND }
        val party = s.partyEnabled && speed != null && average != null &&
            partySince.let { it != null && t - it >= s.partyMinutes * MINUTE } &&
            kotlin.math.abs(speed - average) <= average * s.partyTolerancePercent / 100.0

        val fired = when {
            pokal -> Takeover.POKAL
            fire -> Takeover.FIRE
            party -> Takeover.PARTY
            else -> null
        }
        if (fired != null) {
            lastFired = t
            fireSince = null
            partySince = null
        }
        return fired
    }

    /** How long the heart rate has been in the party zones, for the panel. */
    fun partyMinutes(now: Long): Int = partySince?.let { ((now - it) / MINUTE).toInt() } ?: 0

    private companion object {
        const val SECOND = 1_000L
        const val MINUTE = 60_000L
    }
}
