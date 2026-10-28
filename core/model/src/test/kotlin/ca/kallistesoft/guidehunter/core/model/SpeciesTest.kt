package ca.kallistesoft.guidehunter.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.io.File
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
        assertTrue(Species.ELK in Species.forActivity(GuideActivity.HUNT))
        assertTrue(Species.PACIFIC_SALMON in Species.forActivity(GuideActivity.FISH))
        assertTrue(Species.forActivity(GuideActivity.FISH).all { it.discipline == Discipline.FISHING })
        assertEquals(Species.entries, Species.forActivity(GuideActivity.BOTH))
    }

    @Test
    fun `stored names come back as species, unknown ones are dropped`() {
        assertEquals(listOf(Species.MOOSE, Species.WALLEYE), Species.fromNames(listOf("MOOSE", "DODO", "WALLEYE")))
    }

    @Test
    fun `firestore rules accept every species, and all of them at once`() {
        // Tests run in the module's directory; the rules are at the repository's root.
        val rules = File("../../firestore.rules").readText()
        val listed = Regex("data\\.species\\.hasOnly\\(\\[(.*?)]\\)", RegexOption.DOT_MATCHES_ALL).find(rules)!!.groupValues[1]
        assertEquals(Species.entries.map { it.name }.toSet(), Regex("'(\\w+)'").findAll(listed).map { it.groupValues[1] }.toSet())
        assertTrue("data.species.size() <= ${Species.entries.size}" in rules)
    }

    @Test
    fun `firestore rules accept an offer's schedule fields`() {
        val rules = File("../../firestore.rules").readText()
        listOf("'tripLength'", "'departures'", "'everyYear'", "data.tripLength <= ${TripLength.MAX_DAYS}", "data.departures.size() <= ${OfferDraft.MAX_DEPARTURES}")
            .forEach { assertTrue("rules have $it", it in rules) }
    }
}
