package de.karoofun.karoo

import android.content.Context

/**
 * User settings: own heart rate zones and the full-page comic actions.
 * Speeds in km/h, grade in percent, heart rates in bpm, durations as named.
 */
data class FunSettings(
    val ownZones: Boolean = true,
    val maxHr: Int = 175,
    val zone2From: Int = 105,
    val zone3From: Int = 123,
    val zone4From: Int = 140,
    val zone5From: Int = 158,
    val cooldownMinutes: Int = 25,
    val durationSeconds: Int = 3,
    val pokalEnabled: Boolean = true,
    val pokalSpeedKmh: Int = 60,
    val pokalGrade: Int = -2,
    val fireEnabled: Boolean = true,
    val fireAbovePercent: Int = 15,
    val fireMinZone: Int = 4,
    val fireSeconds: Int = 10,
    val partyEnabled: Boolean = true,
    val partyMinZone: Int = 2,
    val partyMaxZone: Int = 3,
    val partyMinutes: Int = 20,
    val partyTolerancePercent: Int = 5,
    /** Own number of chainrings and cogs for the gear field; off uses what the shifting reports. */
    val ownGears: Boolean = false,
    val frontGears: Int = 2,
    val rearGears: Int = 12,
    /** "de" or "en" for a fixed language, empty for the system language. */
    val language: String = LANGUAGE_AUTO,
) {
    /** Zone starts for Z1..Z5, used when [ownZones] is on. */
    val zoneStarts get() = listOf(0, zone2From, zone3From, zone4From, zone5From)

    /** New max heart rate with the zones recalculated at the usual 60/70/80/90 %. */
    fun withMaxHr(max: Int) = copy(
        maxHr = max,
        zone2From = percentOf(max, 60),
        zone3From = percentOf(max, 70),
        zone4From = percentOf(max, 80),
        zone5From = percentOf(max, 90),
    )

    fun save(context: Context) {
        prefs(context).edit()
            .putBoolean(OWN_ZONES, ownZones)
            .putInt(MAX_HR, maxHr)
            .putInt(ZONE_2, zone2From)
            .putInt(ZONE_3, zone3From)
            .putInt(ZONE_4, zone4From)
            .putInt(ZONE_5, zone5From)
            .putInt(COOLDOWN, cooldownMinutes)
            .putInt(DURATION, durationSeconds)
            .putBoolean(POKAL, pokalEnabled)
            .putInt(POKAL_SPEED, pokalSpeedKmh)
            .putInt(POKAL_GRADE, pokalGrade)
            .putBoolean(FIRE, fireEnabled)
            .putInt(FIRE_ABOVE, fireAbovePercent)
            .putInt(FIRE_ZONE, fireMinZone)
            .putInt(FIRE_SECONDS, fireSeconds)
            .putBoolean(PARTY, partyEnabled)
            .putInt(PARTY_MIN_ZONE, partyMinZone)
            .putInt(PARTY_MAX_ZONE, partyMaxZone)
            .putInt(PARTY_MINUTES, partyMinutes)
            .putInt(PARTY_TOLERANCE, partyTolerancePercent)
            .putString(LANGUAGE, language)
            .putBoolean(OWN_GEARS, ownGears)
            .putInt(FRONT_GEARS, frontGears)
            .putInt(REAR_GEARS, rearGears)
            .apply()
    }

    companion object {
        /** Allowed ranges, shared by the settings screen and loading. */
        val MAX_HR_RANGE = 120..220
        val ZONE_START_RANGE = 60..220
        val COOLDOWN_RANGE = 5..60
        val DURATION_RANGE = 1..3
        val POKAL_SPEED_RANGE = 30..100
        val POKAL_GRADE_RANGE = -10..0
        val FIRE_ABOVE_RANGE = 5..50
        val ZONE_RANGE = 1..5
        val FIRE_SECONDS_RANGE = 0..60
        val PARTY_MINUTES_RANGE = 5..60
        val PARTY_TOLERANCE_RANGE = 1..15
        val FRONT_GEARS_RANGE = 1..2
        val REAR_GEARS_RANGE = 8..14
        const val LANGUAGE_AUTO = ""
        val LANGUAGES = listOf(LANGUAGE_AUTO, "de", "en")

        fun load(context: Context): FunSettings {
            val p = prefs(context)
            val d = FunSettings()
            return FunSettings(
                ownZones = p.getBoolean(OWN_ZONES, d.ownZones),
                maxHr = p.getInt(MAX_HR, d.maxHr).coerceIn(MAX_HR_RANGE),
                zone2From = p.getInt(ZONE_2, d.zone2From).coerceIn(ZONE_START_RANGE),
                zone3From = p.getInt(ZONE_3, d.zone3From).coerceIn(ZONE_START_RANGE),
                zone4From = p.getInt(ZONE_4, d.zone4From).coerceIn(ZONE_START_RANGE),
                zone5From = p.getInt(ZONE_5, d.zone5From).coerceIn(ZONE_START_RANGE),
                cooldownMinutes = p.getInt(COOLDOWN, d.cooldownMinutes).coerceIn(COOLDOWN_RANGE),
                durationSeconds = p.getInt(DURATION, d.durationSeconds).coerceIn(DURATION_RANGE),
                pokalEnabled = p.getBoolean(POKAL, d.pokalEnabled),
                pokalSpeedKmh = p.getInt(POKAL_SPEED, d.pokalSpeedKmh).coerceIn(POKAL_SPEED_RANGE),
                pokalGrade = p.getInt(POKAL_GRADE, d.pokalGrade).coerceIn(POKAL_GRADE_RANGE),
                fireEnabled = p.getBoolean(FIRE, d.fireEnabled),
                fireAbovePercent = p.getInt(FIRE_ABOVE, d.fireAbovePercent).coerceIn(FIRE_ABOVE_RANGE),
                fireMinZone = p.getInt(FIRE_ZONE, d.fireMinZone).coerceIn(ZONE_RANGE),
                fireSeconds = p.getInt(FIRE_SECONDS, d.fireSeconds).coerceIn(FIRE_SECONDS_RANGE),
                partyEnabled = p.getBoolean(PARTY, d.partyEnabled),
                partyMinZone = p.getInt(PARTY_MIN_ZONE, d.partyMinZone).coerceIn(ZONE_RANGE),
                partyMaxZone = p.getInt(PARTY_MAX_ZONE, d.partyMaxZone).coerceIn(ZONE_RANGE),
                partyMinutes = p.getInt(PARTY_MINUTES, d.partyMinutes).coerceIn(PARTY_MINUTES_RANGE),
                partyTolerancePercent = p.getInt(PARTY_TOLERANCE, d.partyTolerancePercent).coerceIn(PARTY_TOLERANCE_RANGE),
                language = p.getString(LANGUAGE, d.language).takeIf { it in LANGUAGES } ?: d.language,
                ownGears = p.getBoolean(OWN_GEARS, d.ownGears),
                frontGears = p.getInt(FRONT_GEARS, d.frontGears).coerceIn(FRONT_GEARS_RANGE),
                rearGears = p.getInt(REAR_GEARS, d.rearGears).coerceIn(REAR_GEARS_RANGE),
            )
        }

        private fun prefs(context: Context) = context.getSharedPreferences("fun_settings", Context.MODE_PRIVATE)

        private const val OWN_ZONES = "own_zones"
        private const val MAX_HR = "max_hr"
        private const val ZONE_2 = "zone2_from"
        private const val ZONE_3 = "zone3_from"
        private const val ZONE_4 = "zone4_from"
        private const val ZONE_5 = "zone5_from"
        private const val COOLDOWN = "cooldown_minutes"
        private const val DURATION = "duration_seconds"
        private const val POKAL = "pokal_enabled"
        private const val POKAL_SPEED = "pokal_speed_kmh"
        private const val POKAL_GRADE = "pokal_grade"
        private const val FIRE = "fire_enabled"
        private const val FIRE_ABOVE = "fire_above_percent"
        private const val FIRE_ZONE = "fire_min_zone"
        private const val FIRE_SECONDS = "fire_seconds"
        private const val PARTY = "party_enabled"
        private const val PARTY_MIN_ZONE = "party_min_zone"
        private const val PARTY_MAX_ZONE = "party_max_zone"
        private const val PARTY_MINUTES = "party_minutes"
        private const val PARTY_TOLERANCE = "party_tolerance_percent"
        private const val LANGUAGE = "language"
        private const val OWN_GEARS = "own_gears"
        private const val FRONT_GEARS = "front_gears"
        private const val REAR_GEARS = "rear_gears"
    }
}
