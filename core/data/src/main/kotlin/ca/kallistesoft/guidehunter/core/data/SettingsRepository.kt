package ca.kallistesoft.guidehunter.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.model.AppSettings
import ca.kallistesoft.guidehunter.core.model.Species
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The app's settings on this phone (not the account): they work signed out, and stay after a sign-out. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** The welcome is done; the app opens on [mode] from now on. */
    suspend fun finishOnboarding(mode: AppMode)

    suspend fun setMode(mode: AppMode)

    /** The species the map shows (empty: any species). */
    suspend fun setSpeciesFilter(species: Set<Species>)

    /** Back to a first launch (the account was deleted). */
    suspend fun reset()
}

/** The settings in a Preferences DataStore. */
class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    override val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            onboarded = prefs[ONBOARDED] ?: false,
            mode = prefs[MODE]?.let { name -> AppMode.entries.find { it.name == name } } ?: AppMode.USER,
            // By name; a species removed since (or unknown to an older version) is dropped.
            speciesFilter = Species.fromNames(prefs[SPECIES_FILTER].orEmpty()).toSet(),
        )
    }

    override suspend fun finishOnboarding(mode: AppMode) {
        store.edit {
            it[ONBOARDED] = true
            it[MODE] = mode.name
        }
    }

    override suspend fun setMode(mode: AppMode) {
        store.edit { it[MODE] = mode.name }
    }

    override suspend fun setSpeciesFilter(species: Set<Species>) {
        store.edit { it[SPECIES_FILTER] = species.map { s -> s.name }.toSet() }
    }

    override suspend fun reset() {
        store.edit { it.clear() }
    }

    private companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val MODE = stringPreferencesKey("mode")
        val SPECIES_FILTER = stringSetPreferencesKey("species_filter")
    }
}
