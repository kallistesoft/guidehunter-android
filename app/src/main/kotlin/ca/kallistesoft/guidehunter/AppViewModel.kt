package ca.kallistesoft.guidehunter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import ca.kallistesoft.guidehunter.core.data.AuthRepository
import ca.kallistesoft.guidehunter.core.data.SettingsRepository
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.navigation.GuideMap
import ca.kallistesoft.guidehunter.navigation.MyOffers
import ca.kallistesoft.guidehunter.navigation.Welcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The app's home, from the settings and the account: the welcome on a first launch, then the map (user mode) or the
 * guide's offers (guide mode, only with an account: signed out, it's the map). Null until the settings are read: the
 * splash screen stays until then.
 */
@HiltViewModel
class AppViewModel @Inject constructor(settings: SettingsRepository, auth: AuthRepository) : ViewModel() {
    val home: StateFlow<NavKey?> = combine(settings.settings, auth.user) { settings, user ->
        when {
            !settings.onboarded -> Welcome
            settings.mode == AppMode.GUIDE && user != null -> MyOffers
            else -> GuideMap
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
