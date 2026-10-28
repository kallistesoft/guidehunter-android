package ca.kallistesoft.guidehunter.feature.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kallistesoft.guidehunter.core.data.AuthRepository
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.data.UserAccountRepository
import ca.kallistesoft.guidehunter.core.model.ContactField
import ca.kallistesoft.guidehunter.core.model.UserContact
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

/** Where saving a form stands. */
enum class SaveState { EDITING, SAVING, SAVED, FAILED, NOT_CONFIGURED }

data class MyContactUiState(
    /** The saved contact is still being read. */
    val loading: Boolean = true,
    /** The email is the Google account's, shown but not edited. */
    val form: UserContact = UserContact(),
    /** The fields to flag: shown only after a first try to save. */
    val errors: Set<ContactField> = emptySet(),
    val save: SaveState = SaveState.EDITING,
)

/**
 * What the user shares with a guide when they're interested: their name (the Google name at first) and an optional
 * phone; the email is the account's. Read once, kept as typed (and across process death), saved (Pattern B of
 * .claude/skills/viewmodel-state).
 */
@HiltViewModel
class MyContactViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val userAccount: UserAccountRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MyContactUiState())

    val uiState: StateFlow<MyContactUiState> = _uiState.asStateFlow()
        .onStart { if (_uiState.value.loading) load() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value)

    private suspend fun load() {
        val user = auth.user.first()
        val saved = user?.let { userAccount.contact(it.uid).first() } ?: UserContact(name = user?.name.orEmpty())
        // Typed before process death: kept over the saved contact.
        val typed = savedState.get<Array<String>>(KEY_TEXT)
        val form = (typed?.let { UserContact(it[0], phone = it[1]) } ?: saved).copy(email = user?.email.orEmpty())
        val showErrors = savedState.get<Boolean>(KEY_SHOW_ERRORS) == true
        _uiState.update { it.copy(loading = false, form = form, errors = if (showErrors) form.errors() else emptySet()) }
        save()
    }

    fun setName(value: String) = edit { it.copy(name = value) }

    fun setPhone(value: String) = edit { it.copy(phone = value) }

    /** Saves the contact once it's valid; until then, shows what's missing. */
    fun submit() {
        val state = _uiState.value
        if (state.loading || state.save == SaveState.SAVING || state.save == SaveState.SAVED) return
        if (!state.form.isComplete) {
            savedState[KEY_SHOW_ERRORS] = true
            _uiState.update { it.copy(errors = it.form.errors()) }
            return
        }
        _uiState.update { it.copy(save = SaveState.SAVING) }
        viewModelScope.launch {
            val save = try {
                val user = checkNotNull(auth.user.first()) { "Signed out" }
                userAccount.saveContact(user.uid, state.form)
                SaveState.SAVED
            } catch (e: CancellationException) {
                throw e
            } catch (_: BackendNotConfiguredException) {
                SaveState.NOT_CONFIGURED
            } catch (_: Exception) {
                SaveState.FAILED
            }
            _uiState.update { it.copy(save = save) }
        }
    }

    private fun edit(change: (UserContact) -> UserContact) {
        _uiState.update { state ->
            val form = change(state.form)
            val failed = state.save == SaveState.FAILED || state.save == SaveState.NOT_CONFIGURED
            state.copy(
                form = form,
                errors = if (savedState.get<Boolean>(KEY_SHOW_ERRORS) == true) form.errors() else emptySet(),
                save = if (failed) SaveState.EDITING else state.save,
            )
        }
        save()
    }

    private fun save() {
        val form = _uiState.value.form
        savedState[KEY_TEXT] = arrayOf(form.name, form.phone)
    }

    private companion object {
        const val KEY_TEXT = "text"
        const val KEY_SHOW_ERRORS = "showErrors"
    }
}
