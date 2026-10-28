package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kallistesoft.guidehunter.core.ads.NativeAds
import ca.kallistesoft.guidehunter.core.data.AuthRepository
import ca.kallistesoft.guidehunter.core.data.GuideAccountRepository
import ca.kallistesoft.guidehunter.core.data.RequestRepository
import ca.kallistesoft.guidehunter.core.model.InterestRequest
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why accepting or declining didn't go through. */
enum class ReceivedError {
    /** Offline, or refused. */
    FAILED,

    /** Accepting gives the guide's profile contact: there's none yet. */
    NEEDS_PROFILE,
}

data class RequestsReceivedUiState(
    val loading: Boolean = true,
    /** Waiting for the guide's answer, the newest first. */
    val pending: List<InterestRequest> = emptyList(),
    /** Accepted, declined, cancelled by the user, or closed with their offer. */
    val answered: List<InterestRequest> = emptyList(),
    /** An answer is on its way: the buttons wait. */
    val busy: Boolean = false,
    val error: ReceivedError? = null,
)

/**
 * "Requests received": the requests to the guide's offers, live, the pending ones to accept (giving their profile's
 * contact) or decline (Pattern A of .claude/skills/viewmodel-state). Read while the screen shows; no polling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RequestsReceivedViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val requests: RequestRepository,
    private val account: GuideAccountRepository,
    /** Serves the ad card after the pending requests. */
    val nativeAds: NativeAds,
) : ViewModel() {
    private data class Work(val busy: Boolean = false, val error: ReceivedError? = null)

    private val work = MutableStateFlow(Work())

    val uiState: StateFlow<RequestsReceivedUiState> = auth.user.flatMapLatest { user ->
        if (user == null) {
            flowOf(RequestsReceivedUiState(loading = false))
        } else {
            combine(requests.received(user.uid), work) { received, work ->
                val (pending, answered) = received.partition { it.status == RequestStatus.PENDING }
                RequestsReceivedUiState(loading = false, pending = pending, answered = answered, busy = work.busy, error = work.error)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RequestsReceivedUiState())

    /** Accepts the request: the user gets the guide's profile contact. */
    fun accept(id: String) = run {
        val user = checkNotNull(auth.user.first()) { "Signed out" }
        val profile = account.profile(user.uid).first()
        if (profile == null || !profile.isComplete) return@run ReceivedError.NEEDS_PROFILE
        requests.approve(id, profile)
        null
    }

    /** Declines the request (the screen asked to confirm). */
    fun decline(id: String) = run {
        requests.reject(id)
        null
    }

    fun dismissError() = work.update { it.copy(error = null) }

    private fun run(action: suspend () -> ReceivedError?) {
        if (work.value.busy) return
        work.value = Work(busy = true)
        viewModelScope.launch {
            val error = try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ReceivedError.FAILED
            }
            work.value = Work(error = error)
        }
    }
}
