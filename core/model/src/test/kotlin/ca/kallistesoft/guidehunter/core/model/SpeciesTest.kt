package ca.kallistesoft.guidehunter.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesTest {
    @Test
    fun `a guide's activity covers its disciplines`() {
        assertTrue(GuideActivity.HUNT.covers(Discipline.HUNTING))
        assertFalse(GuideActivity.HUNT.covers(Discipline.FISHING))
        assertTrue(GuideActivity.FISH.covers(Discipline.FISHING))
        assertTrue(GuideActivity.BOTH.covers(Discipline.HUNTING) && GuideActivity.BOTH.covers(Discipline.FISHING))
    }

    @Test
    fun `the species list follows the activity`() {
        assertTrue(Species.forActivity(GuideActivity.HUNT).all { it.discipline == Discipline.HUNTING })
        assertTrue(Species.BLACK_BEAR in Species.forActivity(GuideActivity.HUNT))
        assertTrue(Species.forActivity(GuideActivity.FISH).all { it.discipline == Discipline.FISHING })
        assertEquals(Species.entries, Species.forActivity(GuideActivity.BOTH))
    }

    @Test
    fun `stored names come back as species, unknown ones are dropped`() {
        assertEquals(listOf(Species.MOOSE, Species.WALLEYE), Species.fromNames(listOf("MOOSE", "DODO", "WALLEYE")))
    }
}
