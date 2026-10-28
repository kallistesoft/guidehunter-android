package ca.kallistesoft.guidehunter.core.model

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideFilterTest {
    private fun guide(activity: GuideActivity, vararg species: Species) =
        Guide("id", "title", "", activity, species.toList(), "", GeoPoint(0.0, 0.0))

    private val today = LocalDate(2027, 4, 15)
    private val bear = guide(GuideActivity.HUNT, Species.BLACK_BEAR)
    private val camp = guide(GuideActivity.BOTH, Species.MOOSE, Species.WALLEYE)
    private val salmon = guide(GuideActivity.FISH, Species.ATLANTIC_SALMON)

    @Test
    fun `no filter shows every guide`() {
        assertTrue(listOf(bear, camp, salmon).all({ GuideFilter().matches(it, today) }))
    }

    @Test
    fun `a discipline shows the guides that offer it, both included`() {
        val hunting = GuideFilter(Discipline.HUNTING)
        assertEquals(listOf(bear, camp), listOf(bear, camp, salmon).filter { hunting.matches(it, today) })
    }

    @Test
    fun `species show the guides that offer any of them`() {
        assertTrue(GuideFilter(species = setOf(Species.WALLEYE)).matches(camp, today))
        assertFalse(GuideFilter(species = setOf(Species.WALLEYE)).matches(salmon, today))
        val both = GuideFilter(species = setOf(Species.WALLEYE, Species.ATLANTIC_SALMON))
        assertEquals(listOf(camp, salmon), listOf(bear, camp, salmon).filter { both.matches(it, today) })
    }

    @Test
    fun `the species picker offers the discipline's species`() {
        assertEquals(Species.entries, GuideFilter().speciesChoices)
        assertTrue(GuideFilter(Discipline.FISHING).speciesChoices.all { it.discipline == Discipline.FISHING })
    }

    @Test
    fun `a trip has to fit the dates, dates on request always do, and past offers never show`() {
        val spring = Schedule(TripLength.Days(7), listOf(Departure(LocalDate(2027, 5, 22))))
        val bearInSpring = bear.copy(schedule = spring)
        val may = GuideFilter(dates = Trip(LocalDate(2027, 5, 18), LocalDate(2027, 5, 31)))
        val july = GuideFilter(dates = Trip(LocalDate(2027, 7, 2)))
        assertTrue(may.matches(bearInSpring, today))
        assertFalse(july.matches(bearInSpring, today))
        assertTrue("dates on request", july.matches(camp, today))
        val past = LocalDate(2027, 5, 23)
        assertFalse("its only departure is over", GuideFilter().matches(bearInSpring, past))
        assertTrue(GuideFilter().matches(camp, past))
    }
}
