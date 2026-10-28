package ca.kallistesoft.guidehunter.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kallistesoft.guidehunter.core.auth.AccountSession
import ca.kallistesoft.guidehunter.core.data.SettingsRepository
import ca.kallistesoft.guidehunter.core.model.AppMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [email]: the account just signed in with (empty until known). */
data class ChooseModeUiState(val email: String = "")

/** After sign-in: what the app opens on, the map (user) or the guide's offers (guide). */
@HiltViewModel
class ChooseModeViewModel @Inject constructor(
    session: AccountSession,
    private val settings: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<ChooseModeUiState> = session.user.map { ChooseModeUiState(it?.email.orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChooseModeUiState())

    /** The welcome is done; the app moves to [mode]'s home. */
    fun choose(mode: AppMode) {
        viewModelScope.launch { settings.finishOnboarding(mode) }
    }
}
