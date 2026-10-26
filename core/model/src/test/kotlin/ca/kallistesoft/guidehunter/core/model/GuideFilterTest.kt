package ca.kallistesoft.guidehunter.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideFilterTest {
    private fun guide(activity: GuideActivity, vararg species: Species) =
        Guide("id", "title", "", activity, species.toList(), "", GeoPoint(0.0, 0.0))

    private val bear = guide(GuideActivity.HUNT, Species.BLACK_BEAR)
    private val camp = guide(GuideActivity.BOTH, Species.MOOSE, Species.WALLEYE)
    private val salmon = guide(GuideActivity.FISH, Species.ATLANTIC_SALMON)

    @Test
    fun `no filter shows every guide`() {
        assertTrue(listOf(bear, camp, salmon).all(GuideFilter()::matches))
    }

    @Test
    fun `a discipline shows the guides that offer it, both included`() {
        val hunting = GuideFilter(Discipline.HUNTING)
        assertEquals(listOf(bear, camp), listOf(bear, camp, salmon).filter(hunting::matches))
    }

    @Test
    fun `a species shows the guides that offer it`() {
        assertTrue(GuideFilter(species = Species.WALLEYE).matches(camp))
        assertFalse(GuideFilter(species = Species.WALLEYE).matches(salmon))
    }

    @Test
    fun `the species picker offers the discipline's species`() {
        assertEquals(Species.entries, GuideFilter().speciesChoices)
        assertTrue(GuideFilter(Discipline.FISHING).speciesChoices.all { it.discipline == Discipline.FISHING })
    }
}
