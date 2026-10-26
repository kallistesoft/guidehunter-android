package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.data.GuideApplicationRepository
import ca.kallistesoft.guidehunter.core.data.LocationRepository
import ca.kallistesoft.guidehunter.core.model.ApplicationField
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.GuideApplication
import ca.kallistesoft.guidehunter.core.model.Species
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SendState { EDITING, SENDING, SENT, FAILED, NOT_CONFIGURED }

data class BecomeGuideUiState(
    val form: GuideApplication = GuideApplication(),
    /** The fields to flag: shown only after a first try to send, not while the guide is still typing. */
    val errors: Set<ApplicationField> = emptySet(),
    val send: SendState = SendState.EDITING,
    /** "Use my location" found none (location off, no fix): the pin has to be placed another way. */
    val locationFailed: Boolean = false,
)

/**
 * The "Become a guide" form: kept as typed (and across process death), checked by [GuideApplication.errors], sent to
 * us for review by email (Pattern A of .claude/skills/viewmodel-state).
 */
@HiltViewModel
class BecomeGuideViewModel @Inject constructor(
    private val repository: GuideApplicationRepository,
    private val locationRepository: LocationRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val form = MutableStateFlow(restore())
    private val showErrors = MutableStateFlow(savedState[KEY_SHOW_ERRORS] ?: false)
    /** Sent stays sent across process death, so the form can't go twice; a send in progress starts over. */
    private val send = MutableStateFlow(if (savedState.get<Boolean>(KEY_SENT) == true) SendState.SENT else SendState.EDITING)

    private val locationFailed = MutableStateFlow(false)

    val uiState: StateFlow<BecomeGuideUiState> = combine(form, showErrors, send, locationFailed) { form, showErrors, send, failed ->
        BecomeGuideUiState(form, if (showErrors) form.errors() else emptySet(), send, failed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState())

    private fun initialState() =
        BecomeGuideUiState(form.value, if (showErrors.value) form.value.errors() else emptySet(), send.value)

    fun setBusinessName(value: String) = edit { it.copy(businessName = value) }

    fun setDescription(value: String) = edit { it.copy(description = value.take(GuideApplication.MAX_DESCRIPTION)) }

    /** Species the new activity doesn't cover are dropped (a fishing guide can't offer moose). */
    fun setActivity(activity: GuideActivity) = edit { form ->
        form.copy(activity = activity, species = form.species.filterTo(mutableSetOf()) { activity.covers(it.discipline) })
    }

    fun toggleSpecies(species: Species) = edit {
        it.copy(species = if (species in it.species) it.species - species else it.species + species)
    }

    fun setRegion(value: String) = edit { it.copy(region = value) }

    fun setLocation(location: GeoPoint) {
        locationFailed.value = false
        edit { it.copy(location = location) }
    }

    /** "Use my location": places the pin where the phone is (the guide is often at the lodge), if Android knows. */
    fun useMyLocation() {
        viewModelScope.launch {
            val location = locationRepository.current()
            if (location != null) setLocation(location) else locationFailed.value = true
        }
    }

    fun setPrice(value: String) = edit { it.copy(priceFrom = value.filter(Char::isDigit).take(MAX_PRICE_DIGITS)) }

    fun setEmail(value: String) = edit { it.copy(email = value) }

    fun setPhone(value: String) = edit { it.copy(phone = value) }

    fun setWebsite(value: String) = edit { it.copy(website = value) }

    /** Sends the form once it's valid; until then, shows what's missing. A second tap while sending does nothing. */
    fun submit() {
        if (send.value == SendState.SENDING || send.value == SendState.SENT) return
        val application = form.value
        if (application.errors().isNotEmpty()) {
            showErrors.value = true
            savedState[KEY_SHOW_ERRORS] = true
            return
        }
        send.value = SendState.SENDING
        viewModelScope.launch {
            send.value = try {
                repository.submit(application)
                savedState[KEY_SENT] = true
                SendState.SENT
            } catch (e: CancellationException) {
                throw e
            } catch (_: BackendNotConfiguredException) {
                SendState.NOT_CONFIGURED
            } catch (_: Exception) {
                SendState.FAILED
            }
        }
    }

    private fun edit(change: (GuideApplication) -> GuideApplication) {
        form.update(change)
        if (send.value == SendState.FAILED || send.value == SendState.NOT_CONFIGURED) send.value = SendState.EDITING
        save(form.value)
    }

    private fun save(form: GuideApplication) {
        savedState[KEY_TEXT] = arrayOf(
            form.businessName, form.description, form.region, form.priceFrom, form.email, form.phone, form.website,
        )
        savedState[KEY_ACTIVITY] = form.activity?.name
        savedState[KEY_SPECIES] = form.species.map { it.name }.toTypedArray()
        savedState[KEY_LOCATION] = form.location?.let { doubleArrayOf(it.latitude, it.longitude) }
    }

    private fun restore(): GuideApplication {
        val text = savedState.get<Array<String>>(KEY_TEXT) ?: return GuideApplication()
        val location = savedState.get<DoubleArray>(KEY_LOCATION)
        return GuideApplication(
            businessName = text[0],
            description = text[1],
            activity = savedState.get<String>(KEY_ACTIVITY)?.let(GuideActivity::valueOf),
            species = Species.fromNames(savedState.get<Array<String>>(KEY_SPECIES).orEmpty().toList()).toSet(),
            region = text[2],
            location = location?.let { GeoPoint(it[0], it[1]) },
            priceFrom = text[3],
            email = text[4],
            phone = text[5],
            website = text[6],
        )
    }

    private companion object {
        const val KEY_TEXT = "text"
        const val KEY_ACTIVITY = "activity"
        const val KEY_SPECIES = "species"
        const val KEY_LOCATION = "location"
        const val KEY_SHOW_ERRORS = "showErrors"
        const val KEY_SENT = "sent"
        const val MAX_PRICE_DIGITS = 6
    }
}
