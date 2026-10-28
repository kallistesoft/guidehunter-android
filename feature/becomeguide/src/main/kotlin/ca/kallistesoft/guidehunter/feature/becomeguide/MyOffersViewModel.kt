package ca.kallistesoft.guidehunter.feature.becomeguide

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kallistesoft.guidehunter.core.data.AuthRepository
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.data.GuideAccountRepository
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.GuideUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What went wrong with the account, said on the screen until dismissed. */
enum class AccountError { SIGN_IN_FAILED, NOT_CONFIGURED, DELETE_FAILED }

data class MyOffersUiState(
    /** Whether a guide is signed in isn't known yet. */
    val loading: Boolean = true,
    /** The signed-in guide; null shows the sign-in. */
    val user: GuideUser? = null,
    /** Their profile; null until they first save one. */
    val profile: GuideProfile? = null,
    val offers: List<GuideOffer> = emptyList(),
    /** Signing in or deleting the account: the buttons wait. */
    val busy: Boolean = false,
    val error: AccountError? = null,
)

/**
 * The guide's space: Google sign-in, then their profile and offers, live (Pattern A of
 * .claude/skills/viewmodel-state). Signing in and deleting the account show Google's sheet over [Context], the
 * activity the screen passes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyOffersViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val account: GuideAccountRepository,
    private val google: GoogleSignIn,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<AccountError?>(null)

    private val signedIn = auth.user.flatMapLatest { user ->
        if (user == null) {
            flowOf(MyOffersUiState(loading = false))
        } else {
            combine(account.profile(user.uid), account.offers(user.uid)) { profile, offers ->
                MyOffersUiState(loading = false, user = user, profile = profile, offers = offers)
            }
        }
    }

    val uiState: StateFlow<MyOffersUiState> = combine(signedIn, busy, error) { state, busy, error ->
        state.copy(busy = busy, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyOffersUiState())

    /** Google's sheet, then Firebase; closing the sheet is no error. */
    fun signIn(activity: Context) = whileBusy(AccountError.SIGN_IN_FAILED) {
        google.idToken(activity)?.let { auth.signIn(it) }
    }

    fun signOut(activity: Context) {
        viewModelScope.launch {
            auth.signOut()
            runCatching { google.signOut(activity) } // only forgets the chosen account: not worth an error
        }
    }

    /**
     * Deletes the guide's offers (off the map too), profile and account. Firebase asks for a recent sign-in first,
     * so Google's sheet shows again; closing it deletes nothing.
     */
    fun deleteAccount(activity: Context) = whileBusy(AccountError.DELETE_FAILED) {
        val user = auth.user.first() ?: return@whileBusy
        val token = google.idToken(activity) ?: return@whileBusy
        auth.reauthenticate(token)
        account.deleteAll(user.uid)
        auth.deleteUser()
        runCatching { google.signOut(activity) }
    }

    fun dismissError() {
        error.value = null
    }

    private fun whileBusy(failure: AccountError, work: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        error.value = null
        viewModelScope.launch {
            try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (_: BackendNotConfiguredException) {
                error.value = AccountError.NOT_CONFIGURED
            } catch (_: Exception) {
                error.value = failure
            } finally {
                busy.value = false
            }
        }
    }
}
