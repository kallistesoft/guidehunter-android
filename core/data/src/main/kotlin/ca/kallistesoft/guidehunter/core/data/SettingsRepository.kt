package ca.kallistesoft.guidehunter.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The app's settings on this phone (not the account): they work signed out, and stay after a sign-out. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** The welcome is done; the app opens on [mode] from now on. */
    suspend fun finishOnboarding(mode: AppMode)

    suspend fun setMode(mode: AppMode)

    /** Back to a first launch (the account was deleted). */
    suspend fun reset()
}

/** The settings in a Preferences DataStore. */
class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    override val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            onboarded = prefs[ONBOARDED] ?: false,
            mode = prefs[MODE]?.let { name -> AppMode.entries.find { it.name == name } } ?: AppMode.USER,
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

    override suspend fun reset() {
        store.edit { it.clear() }
    }

    private companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val MODE = stringPreferencesKey("mode")
    }
}
