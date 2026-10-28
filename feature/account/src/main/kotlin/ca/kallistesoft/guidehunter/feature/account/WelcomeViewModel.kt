package ca.kallistesoft.guidehunter.feature.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kallistesoft.guidehunter.core.auth.AccountSession
import ca.kallistesoft.guidehunter.core.data.SettingsRepository
import ca.kallistesoft.guidehunter.core.model.AppMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WelcomeUiState(val busy: Boolean = false, val error: AccountError? = null)

/** The first launch: sign in with Google (then user or guide), or "Later", straight to the map as a user. */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val session: AccountSession,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val work = AccountWork(viewModelScope)

    val uiState: StateFlow<WelcomeUiState> = combine(work.busy, work.error, ::WelcomeUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WelcomeUiState())

    /** Google's sheet over [activity]; [onSignedIn] once signed in (closing the sheet is no error). */
    fun signIn(activity: Context, onSignedIn: () -> Unit) = work.run(AccountError.SIGN_IN_FAILED) {
        if (session.signIn(activity)) onSignedIn()
    }

    /** No account for now: the map, as a user. */
    fun later() {
        viewModelScope.launch { settings.finishOnboarding(AppMode.USER) }
    }

    fun dismissError() = work.dismissError()
}
