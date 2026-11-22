package de.karoofun.karoo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FunFactsTest {
    private val minute = 60_000L

    @Test
    fun wattsRunHouseholdAppliances() {
        assertEquals(Appliance.BULB, Appliance.of(180.0))
        assertEquals(3, Appliance.bulbs(180.0))
        assertEquals(1, Appliance.bulbs(20.0))
        assertEquals(Appliance.BLENDER, Appliance.of(250.0))
        assertEquals(Appliance.TOASTER, Appliance.of(800.0))
        assertEquals(Appliance.HAIR_DRYER, Appliance.of(1240.0))
        assertEquals(Appliance.KETTLE, Appliance.of(2000.0))
    }

    @Test
    fun workBrewsEspressos() {
        // 196 W for 2:10 h
        assertEquals(23, espressos(196.0 * 130 * 60 / 1000))
        assertEquals(0, espressos(40.0))
    }

    @Test
    fun zoneCharactersGoFromSlothToRocket() {
        assertEquals(ZoneCharacter.SLOTH, ZoneCharacter.of(1, 7))
        assertEquals(ZoneCharacter.RED_FACE, ZoneCharacter.of(5, 7))
        assertEquals(ZoneCharacter.ROCKET, ZoneCharacter.of(7, 7))
        // A Karoo profile with 5 zones spreads over the same seven
        assertEquals(ZoneCharacter.SLOTH, ZoneCharacter.of(1, 5))
        assertEquals(ZoneCharacter.ROCKET, ZoneCharacter.of(5, 5))
        assertEquals(ZoneCharacter.NO_TALKING, ZoneCharacter.of(3, 5))
    }

    @Test
    fun potBoilsOverFrom110Percent() {
        assertEquals(Pot.LUKEWARM, Pot.of(70))
        assertEquals(Pot.SIMMERING, Pot.of(92))
        assertEquals(Pot.BUBBLING, Pot.of(103))
        assertEquals(Pot.BOILING_OVER, Pot.of(118))
    }

    @Test
    fun pedalingStyleNeedsRiding() {
        assertEquals(PedalStyle.COFFEE_GRINDER, pedalStyle(108.0, 8.0))
        assertEquals(PedalStyle.TRACTOR, pedalStyle(56.0, 5.0))
        assertNull(pedalStyle(88.0, 8.0))
        assertNull(pedalStyle(56.0, 1.0))
        assertNull(pedalStyle(null, 8.0))
    }

    @Test
    fun twentyMinutesInOneGearIsLazy() {
        assertTrue(lazyShifting(0, 21 * minute, 7.0))
        assertFalse(lazyShifting(0, 19 * minute, 7.0))
        assertFalse(lazyShifting(0, 30 * minute, 0.0))
        assertFalse(lazyShifting(null, 30 * minute, 7.0))
    }

    @Test
    fun caloriesTakeTurnsAmongEarnedDishes() {
        // 300 kcal: no döner yet (650), the others take turns every 4 seconds
        val dishes = (0 until 16 step 4).map { Food.at(it, 300.0) }
        assertEquals(listOf(Food.PRETZEL, Food.BRATWURST, Food.PIZZA, Food.WEISSBIER), dishes)
        assertEquals(Food.PRETZEL, Food.at(9, null))
        assertEquals(4, Food.PRETZEL.count(1120.0))
    }

    @Test
    fun gradeMoodsAndSweat() {
        assertEquals(GradeMood.WALL, GradeMood.of(16.0))
        assertEquals(GradeMood.PUSH, GradeMood.of(11.0))
        assertEquals(GradeMood.WHEEE, GradeMood.of(-9.0))
        assertNull(GradeMood.of(4.0))
        assertEquals(0, GradeMood.sweat(5.0))
        assertEquals(1, GradeMood.sweat(7.0))
        assertEquals(4, GradeMood.sweat(25.0))
    }

    @Test
    fun climbProgressRunsFromFootToTop() {
        assertEquals(0f, climbProgress(200.0, 200.0)!!, 0.001f)
        assertEquals(0.75f, climbProgress(200.0, 50.0)!!, 0.001f)
        assertNull(climbProgress(null, 50.0))
    }

    @Test
    fun summitWhenTheClimbEndsAtTheTop() {
        val climbs = ClimbTracker()
        climbs.onElevationToTop(184.0, 0)
        climbs.onElevationToTop(120.0, minute)
        assertEquals(184.0, climbs.total!!, 0.1)
        climbs.onElevationToTop(8.0, 10 * minute)
        climbs.onElevationToTop(null, 11 * minute)
        assertEquals(11 * minute, climbs.summitAt)
        assertEquals(184.0, climbs.summitHeight!!, 0.1)
        assertNull(climbs.total)
        assertTrue(celebratingSummit(climbs.summitAt, 12 * minute))
        assertFalse(celebratingSummit(climbs.summitAt, 13 * minute))
    }

    @Test
    fun summitWhenTheNextClimbFollowsRightAway() {
        val climbs = ClimbTracker()
        climbs.onElevationToTop(90.0, 0)
        climbs.onElevationToTop(4.0, minute)
        climbs.onElevationToTop(140.0, 2 * minute)
        assertEquals(2 * minute, climbs.summitAt)
        assertEquals(140.0, climbs.total!!, 0.1)
    }

    @Test
    fun leavingAClimbHalfwayIsNoSummit() {
        val climbs = ClimbTracker()
        climbs.onElevationToTop(184.0, 0)
        climbs.onElevationToTop(90.0, minute)
        climbs.onElevationToTop(null, 2 * minute)
        assertNull(climbs.summitAt)
    }

    @Test
    fun climbedMetersInLandmarks() {
        assertEquals(Landmark.CATHEDRAL, Landmark.of(240.0))
        assertEquals(1.5, Landmark.CATHEDRAL.times(240.0), 0.05)
        assertEquals(Landmark.EIFFEL_TOWER, Landmark.of(800.0))
        assertEquals(Landmark.ZUGSPITZE, Landmark.of(3100.0))
        assertEquals(Landmark.EVEREST, Landmark.of(9000.0))
    }

    @Test
    fun altitudeBands() {
        assertEquals(AltitudeBand.SEA, AltitudeBand.of(12.0))
        assertEquals(AltitudeBand.LOWLANDS, AltitudeBand.of(380.0))
        assertEquals(AltitudeBand.LOW_MOUNTAINS, AltitudeBand.of(720.0))
        assertEquals(AltitudeBand.ALPINE, AltitudeBand.of(1600.0))
        assertEquals(AltitudeBand.THIN_AIR, AltitudeBand.of(2400.0))
        assertEquals(AltitudeBand.ABOVE_ZUGSPITZE, AltitudeBand.of(3000.0))
    }

    @Test
    fun upAndDownGivesTheBalance() {
        val s = RideSnapshot(ascent = 1240.0, descent = 1180.0, elevation = 520.0)
        assertEquals(60.0, ComicField.UP_DOWN.value(s, Rider(), FunSettings())!!, 0.01)
        assertEquals(1240.0, ComicField.ASCENT.value(s, Rider(), FunSettings())!!, 0.01)
        assertEquals(520.0, ComicField.ALTITUDE.value(s, Rider(), FunSettings())!!, 0.01)
        assertNull(ComicField.UP_DOWN.value(RideSnapshot(ascent = 100.0), Rider(), FunSettings()))
    }
}
