package de.karoofun.karoo

import android.content.Context
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** What we need from the rider's Karoo profile: display units, heart rate zones, FTP and power zones. */
data class Rider(
    val units: Units = Units(),
    val zoneStarts: List<Int> = emptyList(),
    val maxHr: Int = 0,
    val ftp: Int = 0,
    val powerZoneStarts: List<Int> = emptyList(),
) {
    /** Heart rate zone 1..5: the zones from the app settings, or the Karoo profile's when they are switched off. */
    fun zone(heartRate: Double?, settings: FunSettings): Int? = heartRate?.let {
        if (settings.ownZones) heartRateZone(it, settings.zoneStarts, settings.maxHr) else heartRateZone(it, zoneStarts, maxHr)
    }

    /** Max heart rate the zones are based on, with the same fallback as [heartRateZone]. */
    fun maxHr(settings: FunSettings): Int = when {
        settings.ownZones -> settings.maxHr
        maxHr >= MIN_MAX_HR -> maxHr
        else -> DEFAULT_MAX_HR
    }

    /** Power zone from 1 to the number of zones: the Karoo profile's, else the usual 7 zones from the FTP. */
    fun powerZone(watts: Double?): Int? {
        watts ?: return null
        val starts = powerZoneStarts.takeIf { it.size >= 2 }
            ?: ftp.takeIf { it > 0 }?.let { f -> COGGAN_PERCENT.map { percentOf(f, it) } }
            ?: return null
        return starts.indexOfLast { watts >= it }.coerceAtLeast(0) + 1
    }

    companion object {
        /** Starts of the 7 classic power zones in percent of FTP. */
        val COGGAN_PERCENT = listOf(0, 56, 76, 91, 106, 121, 151)

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
                ftp = profile.ftp,
                powerZoneStarts = profile.powerZones.map { it.min },
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

fun Context.fieldLabels() = ComicField.entries.associateWith { getString(it.labelRes) }

fun Context.fieldTexts() = FieldTexts(
    labels = fieldLabels(),
    front = getString(R.string.field_gear_front),
    rear = getString(R.string.field_gear_rear),
    crossChain = getString(R.string.field_cross_chain),
    kcal = getString(R.string.field_kcal),
    food = { food, n ->
        val plural = when (food) {
            Food.PRETZEL -> R.plurals.field_pretzels
            Food.BRATWURST -> R.plurals.field_bratwursts
            Food.PIZZA -> R.plurals.field_pizza
            Food.DONER -> R.plurals.field_doner
            Food.WEISSBIER -> R.plurals.field_weissbier
        }
        resources.getQuantityString(plural, n, n)
    },
    noRoute = getString(R.string.field_no_route),
    noClimb = getString(R.string.field_no_climb),
    stillToGo = getString(R.string.field_still_to_go),
    turnNow = getString(R.string.field_turn_now),
    rain = getString(R.string.field_rain_tag),
    dryFrom = getString(R.string.field_dry_from),
    dry = getString(R.string.field_dry),
    wet = getString(R.string.field_wet),
    nextHour = getString(R.string.field_next_hour),
    nextMinutes = getString(R.string.field_next_minutes),
    jacketIn = getString(R.string.field_jacket_in),
    wetFor = getString(R.string.field_wet_for),
    radarLoading = getString(R.string.radar_loading),
    radarOutside = getString(R.string.radar_outside),
    tailwind = getString(R.string.field_tailwind),
    headwind = getString(R.string.field_headwind),
    crosswind = getString(R.string.field_crosswind),
    noWeather = getString(R.string.field_no_weather),
    lightsOnNow = getString(R.string.field_lights_on_now),
    on = getString(R.string.field_on),
    dusk = getString(R.string.field_dusk),
    brightFrom = getString(R.string.field_bright_from),
    waitingForGps = getString(R.string.radar_waiting_gps),
    sunriseIn = getString(R.string.field_sunrise_in),
    sunsetAt = getString(R.string.field_sunset_at),
    sunriseAt = getString(R.string.field_sunrise_at),
    noFtp = getString(R.string.field_no_ftp),
    appliance = { appliance, bulbs -> applianceText(appliance, bulbs) },
    espressos = { kj, n -> resources.getQuantityString(R.plurals.field_espressos, n, kj, n) },
    zoneCharacters = resources.getStringArray(R.array.field_zone_characters).toList(),
    pot = resources.getStringArray(R.array.field_pot).toList(),
    krrk = getString(R.string.field_krrk),
    coffeeGrinder = getString(R.string.field_coffee_grinder),
    tractor = getString(R.string.field_tractor),
    lazyShifting = getString(R.string.field_lazy_shifting),
    wall = getString(R.string.field_wall),
    push = getString(R.string.field_push),
    wheee = getString(R.string.field_wheee),
    summitBeer = getString(R.string.field_summit_beer),
    summitMadeIt = getString(R.string.field_summit_made_it),
    offRoute = getString(R.string.field_off_route),
    backToRoute = getString(R.string.field_back_to_route),
    blowDry = getString(R.string.field_blow_dry),
    photoStop = getString(R.string.field_photo_stop),
)

/** "= 3 light bulbs", "= a toaster": what the rider's watts would run at home. */
fun Context.applianceText(appliance: Appliance, bulbs: Int): String = when (appliance) {
    Appliance.BULB -> resources.getQuantityString(R.plurals.field_bulbs, bulbs, bulbs)
    Appliance.BLENDER -> getString(R.string.field_blender)
    Appliance.TOASTER -> getString(R.string.field_toaster)
    Appliance.HAIR_DRYER -> getString(R.string.field_hair_dryer)
    Appliance.KETTLE -> getString(R.string.field_kettle)
}

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
    dryUntil = getString(R.string.radar_dry_until),
    wetUntil = getString(R.string.radar_wet_until),
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
    kilowattTop = getString(R.string.kilowatt_top),
    kilowattMid = getString(R.string.kilowatt_mid),
    kilowattBottom = getString(R.string.kilowatt_bottom),
    summitTop = getString(R.string.summit_top),
    summitBottom = getString(R.string.summit_bottom),
    boilTop = getString(R.string.boil_top),
    boilBottom = getString(R.string.boil_bottom),
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
        Takeover.KILOWATT -> {
            val watts = data.power ?: settings.kilowattWatts.toDouble()
            PanelInfo(
                getString(R.string.panel_kilowatt), watts.roundToInt().toString(), "W",
                listOf(Chip(ChipIcon.NONE, applianceText(Appliance.of(watts), Appliance.bulbs(watts))), zoneChip),
            )
        }
        Takeover.SUMMIT -> PanelInfo(
            getString(R.string.panel_summit), "+" + units.elevation(data.summitHeight ?: 0.0), units.elevationUnit,
            listOf(Chip(ChipIcon.UP, "${data.ascent?.let { units.elevation(it) } ?: "–"} ${units.elevationUnit}"), zoneChip),
        )
        Takeover.BOILING -> PanelInfo(
            getString(R.string.panel_boiling, settings.boilPercent), String.format("%d:%02d", takeover.boilSeconds / 60, takeover.boilSeconds % 60), "",
            listOf(Chip(ChipIcon.NONE, "${data.power?.roundToInt() ?: "–"} W"), zoneChip),
        )
    }
}
