package ca.kallistesoft.guidehunter.core.ui

import android.app.Application
import ca.kallistesoft.guidehunter.core.model.Discipline
import ca.kallistesoft.guidehunter.core.model.Species
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The pickers' order: hunting then fishing, each alphabetical in the app's language. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpeciesOrderTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    private fun names(groups: List<Pair<Discipline, List<Species>>>) =
        groups.map { (discipline, species) -> discipline to species.map { app.getString(it.label) } }

    @Test
    fun `hunting then fishing, each alphabetical`() {
        val groups = app.speciesByDiscipline(Species.entries)
        assertEquals(listOf(Discipline.HUNTING, Discipline.FISHING), groups.map { it.first })
        assertEquals(Species.entries.size, groups.sumOf { it.second.size })
        groups.forEach { (discipline, species) ->
            assertEquals(true, species.all { it.discipline == discipline })
            val labels = species.map { app.getString(it.label) }
            assertEquals(labels.sortedBy { it.lowercase() }, labels)
        }
        assertEquals("Bighorn sheep", names(groups)[0].second.first())
        assertEquals("Arctic char", names(groups)[1].second.first())
    }

    @Test
    @Config(qualifiers = "fr")
    fun `french labels sort in french, accents with their letter`() {
        val fishing = names(app.speciesByDiscipline(Species.entries))[1].second
        assertEquals("Achigan à grande bouche", fishing.first())
        // É sorts with the E's, not after Z as in String order.
        assertEquals(listOf("Doré", "Esturgeon", "Grand brochet"), fishing.subList(fishing.indexOf("Doré"), fishing.indexOf("Doré") + 3))
    }

    @Test
    fun `a discipline without species is left out`() {
        assertEquals(listOf(Discipline.FISHING to listOf(Species.WALLEYE, Species.BROOK_TROUT).sortedBy { app.getString(it.label) }),
            app.speciesByDiscipline(listOf(Species.WALLEYE, Species.BROOK_TROUT)))
        assertEquals(emptyList<Pair<Discipline, List<Species>>>(), app.speciesByDiscipline(emptyList()))
    }
}
