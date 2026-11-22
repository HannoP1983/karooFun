package de.karoofun.karoo

import kotlin.math.max
import kotlin.math.roundToInt

/** Household appliance that draws about as much power as the rider puts out right now. */
enum class Appliance {
    BULB, BLENDER, TOASTER, HAIR_DRYER, KETTLE;

    companion object {
        private const val BULB_WATTS = 60

        fun of(watts: Double) = when {
            watts < 200 -> BULB
            watts < 600 -> BLENDER
            watts < 1200 -> TOASTER
            watts < 1900 -> HAIR_DRYER
            else -> KETTLE
        }

        /** Number of 60 W bulbs for the lower end, at least one. */
        fun bulbs(watts: Double) = max(1, (watts / BULB_WATTS).roundToInt())
    }
}

/** Espressos the ride's work would brew, counting the machine's heating too (about 65 kJ per cup). */
fun espressos(kiloJoules: Double) = (kiloJoules / KJ_PER_ESPRESSO).toInt()

private const val KJ_PER_ESPRESSO = 65.0

/**
 * The character of a power zone, from sloth to rocket. With the classic 7 zones each zone has its own;
 * a Karoo profile with another number of zones is spread over the same seven.
 */
enum class ZoneCharacter {
    SLOTH, CHATTY, TEMPO, NO_TALKING, RED_FACE, MONSTER, ROCKET;

    companion object {
        fun of(zone: Int, count: Int): ZoneCharacter {
            val last = entries.size - 1
            val index = if (count <= 1) 0 else ((zone - 1).toDouble() * last / (count - 1)).roundToInt()
            return entries[index.coerceIn(0, last)]
        }
    }
}

/** % FTP as a pot on the stove. */
enum class Pot {
    LUKEWARM, SIMMERING, BUBBLING, BOILING_OVER;

    companion object {
        fun of(percent: Int) = when {
            percent < 90 -> LUKEWARM
            percent < 100 -> SIMMERING
            percent < 110 -> BUBBLING
            else -> BOILING_OVER
        }
    }
}

/** How the rider turns the pedals, worth a remark in the gear field. */
enum class PedalStyle { COFFEE_GRINDER, TRACTOR }

/** Spinning from 100 rpm grinds coffee; below 70 rpm while riding along it's a tractor. */
fun pedalStyle(cadence: Double?, speed: Double?): PedalStyle? {
    if (cadence == null || cadence <= 0 || (speed ?: 0.0) < MOVING_SPEED) return null
    return when (pedaling(cadence)) {
        Pedaling.SPINNING -> PedalStyle.COFFEE_GRINDER
        Pedaling.GRINDING -> PedalStyle.TRACTOR
        Pedaling.ROUND -> null
    }
}

/** Riding in the same gear for 20 minutes deserves a question. */
fun lazyShifting(gearSince: Long?, now: Long, speed: Double?) =
    gearSince != null && now - gearSince >= LAZY_SHIFTING_MS && (speed ?: 0.0) >= MOVING_SPEED

private const val LAZY_SHIFTING_MS = 20 * 60_000L

/** Below this speed (m/s) the rider stands, and remarks about riding style would be odd. */
private const val MOVING_SPEED = 2.5

/** What the calories field converts into; it takes turns every few seconds. */
enum class Food(val kcal: Int) {
    PRETZEL(250), BRATWURST(300), PIZZA(280), DONER(650), WEISSBIER(230);

    fun count(kcal: Double) = (kcal / this.kcal).toInt()

    companion object {
        private const val SECONDS_EACH = 4

        /** The dish for this second, among those burned at least once; the pretzel until then. */
        fun at(frame: Int, kcal: Double?): Food {
            val earned = entries.filter { kcal != null && it.count(kcal) >= 1 }.ifEmpty { listOf(PRETZEL) }
            return earned[(frame / SECONDS_EACH) % earned.size]
        }
    }
}

/** The grade field's mood: a wall to push, or a descent to enjoy. */
enum class GradeMood {
    WALL, PUSH, WHEEE;

    companion object {
        fun of(grade: Double) = when {
            grade >= 15 -> WALL
            grade >= 10 -> PUSH
            grade <= -6 -> WHEEE
            else -> null
        }

        /** Sweat drops from 6 % on, one more every 3 %, at most four. */
        fun sweat(grade: Double) = if (grade < 6) 0 else ((grade - 6) / 3).toInt().coerceIn(1, 4)
    }
}

/** How far up the current climb the rider is, 0 at the start and 1 on top; null without a climb. */
fun climbProgress(total: Double?, left: Double?): Float? {
    if (total == null || left == null || total <= 0) return null
    return (1 - left / total).toFloat().coerceIn(0f, 1f)
}

/** The summit field celebrates for a minute and a half after the top. */
fun celebratingSummit(summitAt: Long?, now: Long) = summitAt != null && now - summitAt in 0 until SUMMIT_PARTY_MS

private const val SUMMIT_PARTY_MS = 90_000L

/**
 * Follows the climbs the Karoo reports on a route: how high the current one is in total, for the climber's
 * progress, and when one was topped out. "To the top" counts down and at the summit either clears or jumps up
 * to the next climb.
 */
class ClimbTracker {
    /** Height of the current climb; the largest "to the top" seen since it began. */
    var total: Double? = null
        private set
    var summitAt: Long? = null
        private set
    var summitHeight: Double? = null
        private set
    private var left: Double? = null

    fun onElevationToTop(meters: Double?, now: Long) {
        val last = left
        val nextClimb = meters != null && last != null && meters > last + NEXT_CLIMB_JUMP
        if (last != null && (meters == null || nextClimb)) {
            val height = total ?: 0.0
            if (last <= TOP_LEFT && height >= MIN_SUMMIT) {
                summitAt = now
                summitHeight = height
            }
        }
        total = when {
            meters == null -> null
            nextClimb || last == null -> meters
            else -> max(total ?: meters, meters)
        }
        left = meters
    }

    private companion object {
        /** Meters left when the climb ends that still count as the top. */
        const val TOP_LEFT = 15.0
        const val NEXT_CLIMB_JUMP = 30.0

        /** Smaller bumps are no summit; the takeover asks for more, see [FunSettings.summitMinMeters]. */
        const val MIN_SUMMIT = 20.0
    }
}

/** A landmark to measure the climbed meters in: the cathedral at first, then ever bigger ones. */
enum class Landmark(val meters: Int) {
    CATHEDRAL(157), EIFFEL_TOWER(330), ZUGSPITZE(2962), EVEREST(8849);

    /** How many times this landmark fits into [climbed]. */
    fun times(climbed: Double) = climbed / meters

    companion object {
        fun of(climbed: Double) = when {
            climbed >= EVEREST.meters -> EVEREST
            climbed >= ZUGSPITZE.meters -> ZUGSPITZE
            climbed >= EIFFEL_TOWER.meters -> EIFFEL_TOWER
            else -> CATHEDRAL
        }
    }
}

/** What it is like up there, by meters above sea level. */
enum class AltitudeBand {
    SEA, LOWLANDS, LOW_MOUNTAINS, ALPINE, THIN_AIR, ABOVE_ZUGSPITZE;

    companion object {
        fun of(meters: Double) = when {
            meters < 100 -> SEA
            meters < 500 -> LOWLANDS
            meters < 1000 -> LOW_MOUNTAINS
            meters < 2000 -> ALPINE
            meters < Landmark.ZUGSPITZE.meters -> THIN_AIR
            else -> ABOVE_ZUGSPITZE
        }
    }
}
