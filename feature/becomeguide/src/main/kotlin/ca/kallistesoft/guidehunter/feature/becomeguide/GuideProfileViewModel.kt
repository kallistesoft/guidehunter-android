package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kallistesoft.guidehunter.core.data.AuthRepository
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.data.GuideAccountRepository
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.ProfileField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GuideProfileUiState(
    /** The saved profile is still being read. */
    val loading: Boolean = true,
    val form: GuideProfile = GuideProfile(),
    /** The fields to flag: shown only after a first try to save. */
    val errors: Set<ProfileField> = emptySet(),
    val send: SendState = SendState.EDITING,
)

/**
 * The guide's profile (business name and contact, shared by all their offers): read once, kept as typed (and across
 * process death), saved (Pattern B of .claude/skills/viewmodel-state). A first profile starts with the Google email.
 */
@HiltViewModel
class GuideProfileViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val account: GuideAccountRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(restore())

    val uiState: StateFlow<GuideProfileUiState> = _uiState.asStateFlow()
        .onStart { if (_uiState.value.loading) load() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value)

    private suspend fun load() {
        val user = auth.user.first()
        val profile = user?.let { account.profile(it.uid).first() } ?: GuideProfile(email = user?.email.orEmpty())
        _uiState.update { it.copy(loading = false, form = profile) }
        save()
    }

    fun setBusinessName(value: String) = edit { it.copy(businessName = value) }

    fun setEmail(value: String) = edit { it.copy(email = value) }

    fun setPhone(value: String) = edit { it.copy(phone = value) }

    fun setWebsite(value: String) = edit { it.copy(website = value) }

    /** Saves the profile once it's valid; until then, shows what's missing. */
    fun submit() {
        val state = _uiState.value
        if (state.loading || state.send == SendState.SENDING || state.send == SendState.SENT) return
        if (!state.form.isComplete) {
            savedState[KEY_SHOW_ERRORS] = true
            _uiState.update { it.copy(errors = it.form.errors()) }
            return
        }
        _uiState.update { it.copy(send = SendState.SENDING) }
        viewModelScope.launch {
            val send = try {
                val user = checkNotNull(auth.user.first()) { "Signed out" }
                account.saveProfile(user.uid, state.form)
                SendState.SENT
            } catch (e: CancellationException) {
                throw e
            } catch (_: BackendNotConfiguredException) {
                SendState.NOT_CONFIGURED
            } catch (_: Exception) {
                SendState.FAILED
            }
            _uiState.update { it.copy(send = send) }
        }
    }

    private fun edit(change: (GuideProfile) -> GuideProfile) {
        _uiState.update { state ->
            val form = change(state.form)
            val failed = state.send == SendState.FAILED || state.send == SendState.NOT_CONFIGURED
            state.copy(
                form = form,
                errors = if (savedState.get<Boolean>(KEY_SHOW_ERRORS) == true) form.errors() else emptySet(),
                send = if (failed) SendState.EDITING else state.send,
            )
        }
        save()
    }

    private fun save() {
        val form = _uiState.value.form
        savedState[KEY_TEXT] = arrayOf(form.businessName, form.email, form.phone, form.website)
    }

    private fun restore(): GuideProfileUiState {
        val text = savedState.get<Array<String>>(KEY_TEXT) ?: return GuideProfileUiState()
        val form = GuideProfile(text[0], text[1], text[2], text[3])
        val showErrors = savedState.get<Boolean>(KEY_SHOW_ERRORS) == true
        return GuideProfileUiState(loading = false, form = form, errors = if (showErrors) form.errors() else emptySet())
    }

    private companion object {
        const val KEY_TEXT = "text"
        const val KEY_SHOW_ERRORS = "showErrors"
    }
}
