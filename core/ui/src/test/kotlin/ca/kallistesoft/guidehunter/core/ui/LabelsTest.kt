package ca.kallistesoft.guidehunter.core.ui

import android.app.Application
import ca.kallistesoft.guidehunter.core.model.Discipline
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.Species
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Every species, discipline and activity has its own label, in English and French. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LabelsTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    private fun labels(): List<String> =
        (Species.entries.map { it.label } + Discipline.entries.map { it.label } + GuideActivity.entries.map { it.label })
            .map(app::getString)

    @Test
    fun `every species has a distinct label`() {
        val species = Species.entries.map { app.getString(it.label) }
        assertEquals(species.size, species.distinct().size)
        assertEquals("Black bear", app.getString(Species.BLACK_BEAR.label))
        assertEquals("Hunting and fishing", app.getString(GuideActivity.BOTH.label))
        assertEquals("Fishing", app.getString(Discipline.FISHING.label))
    }

    @Test
    @Config(qualifiers = "fr")
    fun `labels are translated`() {
        assertEquals("Ours noir", app.getString(Species.BLACK_BEAR.label))
        assertEquals("Doré", app.getString(Species.WALLEYE.label))
        assertEquals("Chasse", app.getString(Discipline.HUNTING.label))
        assertEquals(Species.entries.size + 5, labels().size)
    }
}
