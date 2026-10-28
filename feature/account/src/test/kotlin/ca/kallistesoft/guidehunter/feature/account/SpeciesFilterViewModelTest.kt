package ca.kallistesoft.guidehunter.feature.account

import ca.kallistesoft.guidehunter.core.model.AppSettings
import ca.kallistesoft.guidehunter.core.model.Discipline
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.testing.FakeSettingsRepository
import ca.kallistesoft.guidehunter.core.testing.MainDispatcherRule
import ca.kallistesoft.guidehunter.core.testing.collecting
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SpeciesFilterViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository(AppSettings(onboarded = true, speciesFilter = setOf(Species.MOOSE)))

    @Test
    fun `from settings, every species is listed with the chosen ones`() = runTest {
        val vm = SpeciesFilterViewModel(null, settings)
        assertTrue("loading until collected", vm.uiState.value.loading)
        collecting(vm.uiState)
        assertEquals(SpeciesFilterUiState(false, Species.entries, setOf(Species.MOOSE)), vm.uiState.value)
    }

    @Test
    fun `from the map, only the species of its discipline`() = runTest {
        val vm = SpeciesFilterViewModel(Discipline.FISHING, settings)
        collecting(vm.uiState)
        assertTrue(vm.uiState.value.choices.isNotEmpty())
        assertTrue(vm.uiState.value.choices.all { it.discipline == Discipline.FISHING })
    }

    @Test
    fun `each tap is saved at once, and clear means any species`() = runTest {
        val vm = SpeciesFilterViewModel(null, settings)
        collecting(vm.uiState)
        vm.toggle(Species.WALLEYE)
        assertEquals(setOf(Species.MOOSE, Species.WALLEYE), settings.settings.value.speciesFilter)
        vm.toggle(Species.MOOSE)
        assertEquals("ticking again removes it", setOf(Species.WALLEYE), settings.settings.value.speciesFilter)
        assertEquals(setOf(Species.WALLEYE), vm.uiState.value.chosen)
        vm.clear()
        assertEquals(emptySet<Species>(), settings.settings.value.speciesFilter)
        assertEquals(emptySet<Species>(), vm.uiState.value.chosen)
    }
}
