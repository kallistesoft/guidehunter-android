package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kallistesoft.guidehunter.core.data.AuthRepository
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.data.GuideAccountRepository
import ca.kallistesoft.guidehunter.core.data.LocationRepository
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import ca.kallistesoft.guidehunter.core.model.OfferField
import ca.kallistesoft.guidehunter.core.model.Species
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
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

data class OfferEditorUiState(
    /** The offer being edited is still being read. */
    val loading: Boolean = true,
    val form: OfferDraft = OfferDraft(),
    /** An offer already sent, being edited (not a new one). */
    val editing: Boolean = false,
    /** A reviewed version is on the map: it stays there, as it is, until the edit is reviewed. */
    val live: Boolean = false,
    /** The fields to flag: shown only after a first try to send, not while the guide is still typing. */
    val errors: Set<OfferField> = emptySet(),
    val send: SendState = SendState.EDITING,
    /** Sending needs the guide's profile (their contact): it isn't filled in yet. */
    val needsProfile: Boolean = false,
    /** "Use my location" found none (location off, no fix): the pin has to be placed another way. */
    val locationFailed: Boolean = false,
    /** Deleted, or gone (deleted on another phone): the screen closes. */
    val deleted: Boolean = false,
)

/**
 * A guide's offer, new ([offerId] null) or sent before: kept as typed (and across process death), checked by
 * [OfferDraft.errors], sent to us for review (Pattern B of .claude/skills/viewmodel-state: an edit is read once).
 */
@HiltViewModel(assistedFactory = OfferEditorViewModel.Factory::class)
class OfferEditorViewModel @AssistedInject constructor(
    @Assisted private val offerId: String?,
    private val auth: AuthRepository,
    private val account: GuideAccountRepository,
    private val locationRepository: LocationRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(offerId: String?): OfferEditorViewModel
    }

    private val _uiState = MutableStateFlow(restore())

    /** The offer is read when the screen starts, unless process death left the guide's edits to restore. */
    val uiState: StateFlow<OfferEditorUiState> = _uiState.asStateFlow()
        .onStart { if (_uiState.value.loading) load() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value)

    private suspend fun load() {
        val offer = try {
            offerId?.let { id -> auth.user.first()?.let { account.offer(it.uid, id) } }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // unreadable (offline with nothing cached): closed, like a deleted one, rather than shown empty
        }
        if (offerId != null && offer == null) {
            _uiState.update { it.copy(loading = false, deleted = true) }
            return
        }
        _uiState.update { it.copy(loading = false, form = offer?.draft ?: OfferDraft(), live = offer?.live == true) }
        save()
    }

    fun setTitle(value: String) = edit { it.copy(title = value.take(OfferDraft.MAX_TITLE)) }

    fun setDescription(value: String) = edit { it.copy(description = value.take(OfferDraft.MAX_DESCRIPTION)) }

    /** Species the new activity doesn't cover are dropped (a fishing guide can't offer moose). */
    fun setActivity(activity: GuideActivity) = edit { form ->
        form.copy(activity = activity, species = form.species.filterTo(mutableSetOf()) { activity.covers(it.discipline) })
    }

    fun toggleSpecies(species: Species) = edit {
        it.copy(species = if (species in it.species) it.species - species else it.species + species)
    }

    fun setRegion(value: String) = edit { it.copy(region = value) }

    fun setLocation(location: GeoPoint) {
        _uiState.update { it.copy(locationFailed = false) }
        edit { it.copy(location = location) }
    }

    /** "Use my location": places the pin where the phone is (the guide is often at the lodge), if Android knows. */
    fun useMyLocation() {
        viewModelScope.launch {
            val location = locationRepository.current()
            if (location != null) setLocation(location) else _uiState.update { it.copy(locationFailed = true) }
        }
    }

    fun setPrice(value: String) = edit { it.copy(priceFrom = value.filter(Char::isDigit).take(MAX_PRICE_DIGITS)) }

    /**
     * Sends the offer for review once it's valid and the guide's profile is filled in; until then, shows what's
     * missing. A second tap while sending does nothing.
     */
    fun submit() {
        val state = _uiState.value
        if (state.loading || state.send == SendState.SENDING || state.send == SendState.SENT) return
        if (state.form.errors().isNotEmpty()) {
            savedState[KEY_SHOW_ERRORS] = true
            _uiState.update { it.copy(errors = it.form.errors()) }
            return
        }
        _uiState.update { it.copy(send = SendState.SENDING) }
        viewModelScope.launch {
            val send = try {
                val user = checkNotNull(auth.user.first()) { "Signed out" }
                val profile = account.profile(user.uid).first()
                if (profile == null || !profile.isComplete) {
                    _uiState.update { it.copy(send = SendState.EDITING, needsProfile = true) }
                    return@launch
                }
                account.saveOffer(user.uid, offerId, state.form, profile)
                SendState.SENT
            } catch (e: CancellationException) {
                throw e
            } catch (_: BackendNotConfiguredException) {
                SendState.NOT_CONFIGURED
            } catch (_: Exception) {
                SendState.FAILED
            }
            _uiState.update { it.copy(send = send, needsProfile = false) }
        }
    }

    /** Deletes the offer (and takes it off the map); the screen closes once done. */
    fun delete() {
        val state = _uiState.value
        val id = offerId ?: return
        if (state.send == SendState.SENDING) return
        _uiState.update { it.copy(send = SendState.SENDING) }
        viewModelScope.launch {
            try {
                val user = checkNotNull(auth.user.first()) { "Signed out" }
                account.deleteOffer(user.uid, GuideOffer(id, state.form, live = state.live))
                _uiState.update { it.copy(deleted = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(send = SendState.FAILED) }
            }
        }
    }

    private fun edit(change: (OfferDraft) -> OfferDraft) {
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
        val state = _uiState.value
        val form = state.form
        savedState[KEY_TEXT] = arrayOf(form.title, form.description, form.region, form.priceFrom)
        savedState[KEY_ACTIVITY] = form.activity?.name
        savedState[KEY_SPECIES] = form.species.map { it.name }.toTypedArray()
        savedState[KEY_LOCATION] = form.location?.let { doubleArrayOf(it.latitude, it.longitude) }
        savedState[KEY_LIVE] = state.live
    }

    /** The edits process death interrupted, or the loading state when there are none. */
    private fun restore(): OfferEditorUiState {
        val text = savedState.get<Array<String>>(KEY_TEXT) ?: return OfferEditorUiState(editing = offerId != null)
        val location = savedState.get<DoubleArray>(KEY_LOCATION)
        val form = OfferDraft(
            title = text[0],
            description = text[1],
            activity = savedState.get<String>(KEY_ACTIVITY)?.let(GuideActivity::valueOf),
            species = Species.fromNames(savedState.get<Array<String>>(KEY_SPECIES).orEmpty().toList()).toSet(),
            region = text[2],
            location = location?.let { GeoPoint(it[0], it[1]) },
            priceFrom = text[3],
        )
        return OfferEditorUiState(
            loading = false,
            form = form,
            editing = offerId != null,
            live = savedState[KEY_LIVE] ?: false,
            errors = if (savedState.get<Boolean>(KEY_SHOW_ERRORS) == true) form.errors() else emptySet(),
        )
    }

    private companion object {
        const val KEY_TEXT = "text"
        const val KEY_ACTIVITY = "activity"
        const val KEY_SPECIES = "species"
        const val KEY_LOCATION = "location"
        const val KEY_LIVE = "live"
        const val KEY_SHOW_ERRORS = "showErrors"
        const val MAX_PRICE_DIGITS = 6
    }
}
