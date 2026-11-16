package de.karoofun.karoo

import android.content.Context
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** What we need from the rider's Karoo profile: display units and heart rate zones. */
data class Rider(val units: Units = Units(), val zoneStarts: List<Int> = emptyList(), val maxHr: Int = 0) {
    /** Heart rate zone 1..5: the zones from the app settings, or the Karoo profile's when they are switched off. */
    fun zone(heartRate: Double?, settings: FunSettings): Int? = heartRate?.let {
        if (settings.ownZones) heartRateZone(it, settings.zoneStarts, settings.maxHr) else heartRateZone(it, zoneStarts, maxHr)
    }

    companion object {
        fun from(profile: UserProfile?): Rider {
            if (profile == null) return Rider()
            val imperial = UserProfile.PreferredUnit.UnitType.IMPERIAL
            return Rider(
                units = Units(
                    imperialDistance = profile.preferredUnit.distance == imperial,
                    imperialElevation = profile.preferredUnit.elevation == imperial,
                    imperialTemperature = profile.preferredUnit.temperature == imperial,
                ),
                zoneStarts = profile.heartRateZones.map { it.min },
                maxHr = profile.maxHr,
            )
        }
    }
}

suspend fun KarooSystemService.rider(): Rider =
    Rider.from(withTimeoutOrNull(2_000) { consumerFlow<UserProfile>().first() })

fun Context.dashboardTexts() = DashboardTexts(
    waiting = getString(R.string.waiting),
    speed = getString(R.string.field_speed),
    temperature = getString(R.string.field_temperature),
    freezing = getString(R.string.field_freezing),
    hot = getString(R.string.field_hot),
    heartRate = getString(R.string.field_heart_rate),
    distance = getString(R.string.field_distance),
    profile = getString(R.string.field_profile),
    zone = getString(R.string.zone_short),
)

fun Context.radarTexts() = RadarTexts(
    title = getString(R.string.radar_title),
    now = getString(R.string.radar_now),
    forecast = getString(R.string.radar_forecast),
    loading = getString(R.string.radar_loading),
    waitingForGps = getString(R.string.radar_waiting_gps),
    outside = getString(R.string.radar_outside),
    outsideHint = getString(R.string.radar_outside_hint),
    staysDry = getString(R.string.radar_stays_dry),
    staysWet = getString(R.string.radar_stays_wet),
    rainFrom = getString(R.string.radar_rain_from),
    dryFrom = getString(R.string.radar_dry_from),
    offline = getString(R.string.radar_offline),
)

fun Context.takeoverTexts() = TakeoverTexts(
    pokalTop = getString(R.string.pokal_top),
    pokalOr = getString(R.string.pokal_or),
    pokalBottom = getString(R.string.pokal_bottom),
    pokalSayings = resources.getStringArray(R.array.pokal_sayings).toList(),
    fireTop = getString(R.string.fire_top),
    fireBottom = getString(R.string.fire_bottom),
    partyTop = getString(R.string.party_top),
    partyBottom = getString(R.string.party_bottom),
    prost = getString(R.string.prost),
)

/** Live values for the fixed strip at the bottom of a takeover. */
fun Context.panelInfo(takeover: ActiveTakeover, data: RideSnapshot, settings: FunSettings, units: Units): PanelInfo {
    val speed = data.speed?.let { units.speed(it) } ?: "–"
    val average = data.averageSpeed?.let { units.speed(it) } ?: "–"
    val zoneChip = Chip(ChipIcon.HEART, "${getString(R.string.zone_short)}${takeover.zone ?: "–"} · ${data.heartRate?.roundToInt() ?: "–"}")
    return when (takeover.type) {
        Takeover.POKAL -> PanelInfo(
            getString(R.string.panel_over, settings.pokalSpeedKmh.toString(), "km/h"), speed, units.speedUnit,
            listOf(Chip(ChipIcon.DOWN, "${data.grade?.let { units.grade(it) } ?: "–"} %")),
        )
        Takeover.FIRE -> {
            val above = data.speed?.let { s -> data.averageSpeed?.takeIf { it > 0 }?.let { ((s / it - 1) * 100).roundToInt() } }
            PanelInfo(
                getString(R.string.panel_average, average, units.speedUnit), speed, units.speedUnit,
                listOf(Chip(ChipIcon.UP, "+${above ?: "–"} %"), zoneChip),
            )
        }
        Takeover.PARTY -> PanelInfo(
            getString(R.string.panel_average, average, units.speedUnit), speed, units.speedUnit,
            listOf(zoneChip, Chip(ChipIcon.NONE, getString(R.string.panel_steady, takeover.steadyMinutes))),
        )
    }
}
