package de.karoofun.karoo

/**
 * What the gear field draws: number of rings and cogs from the settings or the shifting, the gears in use and
 * teeth per ring and cog, biggest cog first. The Karoo only reports the teeth in use, so the others come from a
 * typical setup of that size, with the reported ones put in.
 */
data class Drivetrain(val rings: List<Int>, val cogs: List<Int>, val frontGear: Int?, val rearGear: Int?) {
    /** Big ring on the two biggest cogs or small ring on the two smallest: the chain runs at a steep angle. */
    val crossChained: Boolean
        get() {
            if (rings.size < 2 || frontGear == null || rearGear == null) return false
            return (frontGear == rings.size && rearGear <= 2) || (frontGear == 1 && rearGear >= cogs.size - 1)
        }

    companion object {
        fun of(gears: Gears, settings: FunSettings): Drivetrain {
            val front = if (settings.ownGears) settings.frontGears else (gears.frontMax ?: settings.frontGears)
            val rear = if (settings.ownGears) settings.rearGears else (gears.rearMax ?: settings.rearGears)
            val frontCount = front.coerceIn(FunSettings.FRONT_GEARS_RANGE)
            val rearCount = rear.coerceIn(FunSettings.REAR_GEARS_RANGE)
            // A single ring reports no front shifting, so it is always gear 1
            val frontGear = if (frontCount == 1) 1 else gears.front?.coerceIn(1, frontCount)
            val rearGear = gears.rear?.coerceIn(1, rearCount)
            val rings = (if (frontCount == 1) listOf(40) else listOf(34, 50)).toMutableList()
            val cogs = CASSETTES.getValue(rearCount).toMutableList()
            if (frontGear != null) gears.frontTeeth?.let { rings[frontGear - 1] = it }
            if (rearGear != null) gears.rearTeeth?.let { cogs[rearGear - 1] = it }
            return Drivetrain(rings, cogs, frontGear, rearGear)
        }

        /** Typical cassettes, biggest cog first. */
        private val CASSETTES = mapOf(
            8 to listOf(28, 24, 21, 18, 16, 14, 12, 11),
            9 to listOf(32, 28, 24, 21, 18, 16, 14, 12, 11),
            10 to listOf(32, 28, 24, 21, 19, 17, 15, 13, 12, 11),
            11 to listOf(32, 28, 25, 22, 20, 18, 16, 14, 13, 12, 11),
            12 to listOf(34, 30, 27, 24, 21, 19, 17, 15, 14, 13, 12, 11),
            13 to listOf(36, 32, 28, 24, 21, 19, 17, 15, 14, 13, 12, 11, 10),
            14 to listOf(38, 34, 30, 27, 24, 21, 19, 17, 15, 14, 13, 12, 11, 10),
        )
    }
}
