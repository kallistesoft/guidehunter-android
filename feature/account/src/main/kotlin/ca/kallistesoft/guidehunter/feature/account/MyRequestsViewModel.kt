package ca.kallistesoft.guidehunter.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kallistesoft.guidehunter.core.ads.NativeAds
import ca.kallistesoft.guidehunter.core.data.AuthRepository
import ca.kallistesoft.guidehunter.core.data.RequestRepository
import ca.kallistesoft.guidehunter.core.data.UserAccountRepository
import ca.kallistesoft.guidehunter.core.model.InterestRequest
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.model.UserContact
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyRequestsUiState(
    val loading: Boolean = true,
    /** The user's requests, the latest change first. */
    val requests: List<InterestRequest> = emptyList(),
    /** A change is on its way: the buttons wait. */
    val busy: Boolean = false,
    /** The last change failed. */
    val failed: Boolean = false,
    /** "Send again" tapped with a complete contact: confirm what's sent. */
    val confirm: InterestRequest? = null,
    /** What would be sent: the saved contact with the account's email. */
    val contact: UserContact? = null,
    /** "Send again" tapped, but the contact isn't complete: open it (once). */
    val askContact: Boolean = false,
)

/**
 * "My requests": every request the user sent, live, with cancel and send again (Pattern A of
 * .claude/skills/viewmodel-state). An accepted one carries the guide's contact.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyRequestsViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val requests: RequestRepository,
    private val userAccount: UserAccountRepository,
    /** Serves the ad card in the list. */
    val nativeAds: NativeAds,
) : ViewModel() {
    private data class Work(
        val busy: Boolean = false,
        val failed: Boolean = false,
        val confirming: String? = null,
        val contactAsked: Boolean = false,
    )

    private val work = MutableStateFlow(Work())

    val uiState: StateFlow<MyRequestsUiState> = auth.user.flatMapLatest { user ->
        if (user == null) {
            flowOf(MyRequestsUiState(loading = false))
        } else {
            combine(requests.sent(user.uid), userAccount.contact(user.uid), work) { sent, saved, work ->
                val contact = (saved ?: UserContact(name = user.name)).copy(email = user.email)
                val confirming = sent.find { it.id == work.confirming && it.status == RequestStatus.CANCELLED }
                MyRequestsUiState(
                    loading = false,
                    requests = sent,
                    busy = work.busy,
                    failed = work.failed,
                    confirm = confirming?.takeIf { contact.isComplete },
                    contact = contact,
                    askContact = confirming != null && !contact.isComplete && !work.contactAsked,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyRequestsUiState())

    fun cancel(id: String) = run { requests.cancel(id) }

    /** "Send again": the confirmation, or the contact form first if the contact isn't complete. */
    fun sendAgain(id: String) = work.update { it.copy(confirming = id, contactAsked = false, failed = false) }

    fun contactAsked() = work.update { it.copy(contactAsked = true) }

    fun dismissConfirm() = work.update { it.copy(confirming = null) }

    /** Sends the confirmed request again, with the contact as it is now. */
    fun confirmSend() {
        val state = uiState.value
        val request = state.confirm ?: return
        val contact = state.contact ?: return
        run {
            requests.resend(request.id, request.trip, contact)
            work.update { it.copy(confirming = null) }
        }
    }

    private fun run(action: suspend () -> Unit) {
        if (work.value.busy) return
        work.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch {
            val failed = try {
                action()
                false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                true
            }
            work.update { it.copy(busy = false, failed = failed) }
        }
    }
}
