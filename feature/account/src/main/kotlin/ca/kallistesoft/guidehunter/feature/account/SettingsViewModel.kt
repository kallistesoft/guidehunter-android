package ca.kallistesoft.guidehunter.feature.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kallistesoft.guidehunter.core.ads.AdConsent
import ca.kallistesoft.guidehunter.core.auth.AccountSession
import ca.kallistesoft.guidehunter.core.data.SettingsRepository
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.model.GuideUser
import ca.kallistesoft.guidehunter.core.model.Species
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    /** Signed-in account; null shows "Sign in with Google". */
    val user: GuideUser? = null,
    val mode: AppMode = AppMode.USER,
    /** The user's region requires a way back to their ad consent choices: show "Privacy options". */
    val privacyOptionsRequired: Boolean = false,
    /** Signing in or deleting the account: the actions wait. */
    val busy: Boolean = false,
    val error: AccountError? = null,
    /** The species the map shows (none: any species), in user mode. */
    val speciesFilter: Set<Species> = emptySet(),
)

/**
 * The settings of both modes: the species the map shows (user mode), switch between user and guide, the account (sign in or out, delete), and the ad
 * privacy options (Pattern A of .claude/skills/viewmodel-state). Google's sheet shows over [Context], the activity
 * the screen passes.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val session: AccountSession,
    private val settings: SettingsRepository,
    private val adConsent: AdConsent,
) : ViewModel() {
    private val work = AccountWork(viewModelScope)

    val uiState: StateFlow<SettingsUiState> = combine(
        session.user,
        settings.settings,
        adConsent.privacyOptionsRequired,
        work.busy,
        work.error,
    ) { user, settings, privacyRequired, busy, error ->
        SettingsUiState(false, user, settings.mode, privacyRequired, busy, error, settings.speciesFilter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** The other mode; guide mode needs an account, so signed out it signs in first (closing the sheet stays). */
    fun switchMode(activity: Context) = work.run(AccountError.SIGN_IN_FAILED) {
        val target = if (settings.settings.first().mode == AppMode.USER) AppMode.GUIDE else AppMode.USER
        if (target == AppMode.GUIDE && session.user.first() == null && !session.signIn(activity)) return@run
        settings.setMode(target)
    }

    fun signIn(activity: Context) = work.run(AccountError.SIGN_IN_FAILED) { session.signIn(activity) }

    fun signOut(activity: Context) {
        viewModelScope.launch { session.signOut(activity) }
    }

    /** Deletes everything after Google confirms the account; the app starts over at the welcome. */
    fun deleteAccount(activity: Context) = work.run(AccountError.DELETE_FAILED) { session.deleteAccount(activity) }

    /** "Privacy options": the ad consent choices again (only offered where the user's region requires it). */
    fun showPrivacyOptions() = adConsent.showPrivacyOptions()

    fun dismissError() = work.dismissError()
}
