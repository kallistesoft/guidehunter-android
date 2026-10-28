package ca.kallistesoft.guidehunter.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.model.AppSettings
import ca.kallistesoft.guidehunter.core.model.Species
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.store() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") }

    @Test
    fun `a first launch, then the welcome done, a mode switch and a reset`() = runTest {
        val repository = DataStoreSettingsRepository(store())
        assertEquals(AppSettings(), repository.settings.first())

        repository.finishOnboarding(AppMode.GUIDE)
        assertEquals(AppSettings(onboarded = true, mode = AppMode.GUIDE), repository.settings.first())

        repository.setMode(AppMode.USER)
        assertEquals(AppSettings(onboarded = true, mode = AppMode.USER), repository.settings.first())

        repository.reset()
        assertEquals(AppSettings(), repository.settings.first())
    }

    @Test
    fun `an unknown mode (from a newer version) opens as a user`() = runTest {
        val store = store()
        store.edit { it[stringPreferencesKey("mode")] = "ADMIN" }
        assertEquals(AppMode.USER, DataStoreSettingsRepository(store).settings.first().mode)
    }

    @Test
    fun `the species filter is kept, cleared, and reset with the rest`() = runTest {
        val repository = DataStoreSettingsRepository(store())
        repository.finishOnboarding(AppMode.USER)
        repository.setSpeciesFilter(setOf(Species.MOOSE, Species.WALLEYE))
        assertEquals(
            AppSettings(onboarded = true, speciesFilter = setOf(Species.MOOSE, Species.WALLEYE)),
            repository.settings.first(),
        )

        repository.setSpeciesFilter(emptySet())
        assertEquals(emptySet<Species>(), repository.settings.first().speciesFilter)

        repository.setSpeciesFilter(setOf(Species.ELK))
        repository.reset()
        assertEquals(AppSettings(), repository.settings.first())
    }

    @Test
    fun `an unknown species name (removed since) is ignored`() = runTest {
        val store = store()
        store.edit { it[stringSetPreferencesKey("species_filter")] = setOf("MOOSE", "DODO") }
        assertEquals(setOf(Species.MOOSE), DataStoreSettingsRepository(store).settings.first().speciesFilter)
    }
}
