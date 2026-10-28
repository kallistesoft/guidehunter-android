package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kallistesoft.guidehunter.core.data.AuthRepository
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.data.GuideAccountRepository
import ca.kallistesoft.guidehunter.core.data.LocationRepository
import ca.kallistesoft.guidehunter.core.data.RequestRepository
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.model.Departure
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import ca.kallistesoft.guidehunter.core.model.OfferField
import ca.kallistesoft.guidehunter.core.model.OfferStep
import ca.kallistesoft.guidehunter.core.model.Schedule
import ca.kallistesoft.guidehunter.core.model.Slot
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.model.Today
import ca.kallistesoft.guidehunter.core.model.TripLength
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class OfferEditorUiState(
    /** The offer being edited is still being read. */
    val loading: Boolean = true,
    val form: OfferDraft = OfferDraft(),
    /** An offer already sent, being edited (not a new one). */
    val editing: Boolean = false,
    /** A reviewed version is on the map: it stays there, as it is, until the edit is reviewed. */
    val live: Boolean = false,
    /** The step on screen: one purpose each (what, species, where, when, price and send). */
    val step: OfferStep = OfferStep.WHAT,
    /** Came from the summary to change a step: Next goes back to it. */
    val returnToReview: Boolean = false,
    /**
     * The fields to flag: those of the steps the guide tried to leave (Next, Send), not while they're still typing.
     */
    val errors: Set<OfferField> = emptySet(),
    val send: SendState = SendState.EDITING,
    /** Sending needs the guide's profile (their contact): it isn't filled in yet. */
    val needsProfile: Boolean = false,
    /** "Use my location" found none (location off, no fix): the pin has to be placed another way. */
    val locationFailed: Boolean = false,
    /** Deleted, or gone (deleted on another phone): the screen closes. */
    val deleted: Boolean = false,
    /** The first day a departure can be. */
    val today: LocalDate = LocalDate.fromEpochDays(0),
    /** Requests for this offer waiting for an answer: deleting it closes them. */
    val pendingRequests: Int = 0,
)

/**
 * A guide's offer, new ([offerId] null) or sent before, in steps ([OfferStep]): kept as typed (and across process
 * death), each step checked by [OfferDraft.errors] before the next, sent to us for review (Pattern B of
 * .claude/skills/viewmodel-state: an edit is read once). A new offer starts on the first step, an edit on the summary.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = OfferEditorViewModel.Factory::class)
class OfferEditorViewModel @AssistedInject constructor(
    @Assisted private val offerId: String?,
    private val auth: AuthRepository,
    private val account: GuideAccountRepository,
    private val locationRepository: LocationRepository,
    private val requests: RequestRepository,
    private val savedState: SavedStateHandle,
    private val today: Today,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(offerId: String?): OfferEditorViewModel
    }

    private val _uiState = MutableStateFlow(restore())

    /** The pending requests for this offer (none for a new one), for the delete dialog. */
    private val pendingRequests = auth.user.flatMapLatest { user ->
        if (user == null || offerId == null) {
            flowOf(0)
        } else {
            requests.received(user.uid).map { received -> received.count { it.offerId == offerId && it.status == RequestStatus.PENDING } }
        }
    }

    /** The offer is read when the screen starts, unless process death left the guide's edits to restore. */
    val uiState: StateFlow<OfferEditorUiState> = combine(
        _uiState.asStateFlow().onStart { if (_uiState.value.loading) load() },
        pendingRequests,
    ) { state, pending -> state.copy(pendingRequests = pending) }
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
        _uiState.update {
            it.copy(
                loading = false,
                form = offer?.draft ?: OfferDraft(),
                live = offer?.live == true,
                step = if (offer != null) OfferStep.REVIEW else OfferStep.WHAT,
            )
        }
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

    /** How long a trip lasts. Switching to or from a half day keeps the dates: mornings, or whole days. */
    fun setLength(length: TripLength) = edit { form ->
        val half = length == TripLength.HalfDay
        val departures = form.schedule.departures
            .map { it.copy(slot = if (half) it.slot ?: Slot.MORNING else null) }
            .distinct()
        form.copy(schedule = form.schedule.copy(length = length, departures = departures))
    }

    /** "Other": a number of days, as typed (blank or 0 is flagged once the guide tries the next step). */
    fun setDays(value: String) = setLength(TripLength.Days(value.filter(Char::isDigit).take(MAX_DAYS_DIGITS).toIntOrNull() ?: 0))

    /** Adds departures on [dates]; on a half day, one per chosen half of the day ([slots]). */
    fun addDepartures(dates: List<LocalDate>, slots: Set<Slot>) = edit { form ->
        val half = form.schedule.length == TripLength.HalfDay
        val added = dates.flatMap { date -> if (half) slots.sorted().map { Departure(date, it) } else listOf(Departure(date)) }
        form.copy(schedule = form.schedule.copy(departures = (form.schedule.departures + added).distinct().sorted()))
    }

    /** A departure every week from [first] to [last] (a season of weekly trips in one go). */
    fun addWeekly(first: LocalDate, last: LocalDate, slots: Set<Slot>) =
        addDepartures(generateSequence(first) { it.plus(1, DateTimeUnit.WEEK) }.takeWhile { it <= last }.toList(), slots)

    fun removeDeparture(departure: Departure) = edit { form ->
        form.copy(schedule = form.schedule.copy(departures = form.schedule.departures - departure))
    }

    /** The same days every year, or these dates only. */
    fun setEveryYear(everyYear: Boolean) = edit { it.copy(schedule = it.schedule.copy(everyYear = everyYear)) }

    /**
     * Next: on to the next step once this one's fields are right (back to the summary when the guide came from it);
     * otherwise flags them. On the summary, sends.
     */
    fun next() {
        val state = _uiState.value
        if (state.loading) return
        if (state.step == OfferStep.REVIEW) return submit()
        if (state.form.errors(state.step).isNotEmpty()) return showErrors(setOf(state.step))
        setStep(if (state.returnToReview) OfferStep.REVIEW else OfferStep.entries[state.step.ordinal + 1], returnToReview = false)
    }

    /**
     * Back a step (or to the summary the guide came from); false when there's none (the first step, an edit's
     * summary): the screen closes.
     */
    fun back(): Boolean {
        val state = _uiState.value
        when {
            state.returnToReview -> setStep(OfferStep.REVIEW, returnToReview = false)
            state.step == OfferStep.WHAT || state.step == OfferStep.REVIEW && state.editing -> return false
            else -> setStep(OfferStep.entries[state.step.ordinal - 1], returnToReview = false)
        }
        return true
    }

    /** "Change" on the summary: opens [step]; Next comes back. */
    fun change(step: OfferStep) = setStep(step, returnToReview = true)

    private fun setStep(step: OfferStep, returnToReview: Boolean) {
        _uiState.update { it.copy(step = step, returnToReview = returnToReview) }
        save()
    }

    /** Flags the fields of [steps] from now on. */
    private fun showErrors(steps: Set<OfferStep>) {
        val shown = shownSteps() + steps
        savedState[KEY_SHOWN] = shown.map { it.name }.toTypedArray()
        _uiState.update { it.copy(errors = it.form.errors().filterTo(mutableSetOf()) { field -> field.step in shown }) }
    }

    private fun shownSteps(): Set<OfferStep> =
        savedState.get<Array<String>>(KEY_SHOWN).orEmpty().mapNotNull { name -> OfferStep.entries.find { it.name == name } }.toSet()

    /**
     * Sends the offer for review once it's valid and the guide's profile is filled in; until then, shows what's
     * missing. A second tap while sending does nothing.
     */
    fun submit() {
        val state = _uiState.value
        if (state.loading || state.send == SendState.SENDING || state.send == SendState.SENT) return
        val errors = state.form.errors()
        if (errors.isNotEmpty()) {
            // Back to the first step to fix; Next returns to the summary.
            showErrors(errors.map { it.step }.toSet())
            val first = errors.minOf { it.step }
            if (first != OfferStep.REVIEW) setStep(first, returnToReview = true)
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
                errors = shownSteps().let { shown -> form.errors().filterTo(mutableSetOf()) { it.step in shown } },
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
        // Days as typed (0 included, still to fix), or a code for a half day and none.
        savedState[KEY_LENGTH] = when (val length = form.schedule.length) {
            TripLength.HalfDay -> HALF_DAY
            is TripLength.Days -> length.count
            null -> NO_LENGTH
        }
        savedState[KEY_DEPARTURES] = form.schedule.departures
            .flatMap { listOf(it.date.toEpochDays(), it.slot?.ordinal?.toLong() ?: -1) }
            .toLongArray()
        savedState[KEY_EVERY_YEAR] = form.schedule.everyYear
        savedState[KEY_STEP] = state.step.name
        savedState[KEY_RETURN] = state.returnToReview
    }

    /** The edits process death interrupted, or the loading state when there are none. */
    private fun restore(): OfferEditorUiState {
        val text = savedState.get<Array<String>>(KEY_TEXT) ?: return OfferEditorUiState(editing = offerId != null, today = today())
        val location = savedState.get<DoubleArray>(KEY_LOCATION)
        val form = OfferDraft(
            title = text[0],
            description = text[1],
            activity = savedState.get<String>(KEY_ACTIVITY)?.let(GuideActivity::valueOf),
            species = Species.fromNames(savedState.get<Array<String>>(KEY_SPECIES).orEmpty().toList()).toSet(),
            region = text[2],
            location = location?.let { GeoPoint(it[0], it[1]) },
            priceFrom = text[3],
            schedule = Schedule(
                length = when (val days = savedState.get<Int>(KEY_LENGTH) ?: NO_LENGTH) {
                    NO_LENGTH -> null
                    HALF_DAY -> TripLength.HalfDay
                    else -> TripLength.Days(days)
                },
                departures = savedState.get<LongArray>(KEY_DEPARTURES)?.toList().orEmpty().chunked(2).map { (day, slot) ->
                    Departure(LocalDate.fromEpochDays(day), Slot.entries.getOrNull(slot.toInt()))
                },
                everyYear = savedState[KEY_EVERY_YEAR] ?: false,
            ),
        )
        return OfferEditorUiState(
            loading = false,
            form = form,
            editing = offerId != null,
            live = savedState[KEY_LIVE] ?: false,
            step = savedState.get<String>(KEY_STEP)?.let(OfferStep::valueOf) ?: OfferStep.WHAT,
            returnToReview = savedState[KEY_RETURN] ?: false,
            errors = shownSteps().let { shown -> form.errors().filterTo(mutableSetOf()) { it.step in shown } },
            today = today(),
        )
    }

    private companion object {
        const val KEY_TEXT = "text"
        const val KEY_ACTIVITY = "activity"
        const val KEY_SPECIES = "species"
        const val KEY_LOCATION = "location"
        const val KEY_LIVE = "live"
        const val KEY_SHOWN = "shownSteps"
        const val KEY_LENGTH = "length"
        const val KEY_DEPARTURES = "departures"
        const val KEY_EVERY_YEAR = "everyYear"
        const val KEY_STEP = "step"
        const val KEY_RETURN = "returnToReview"
        const val MAX_PRICE_DIGITS = 6
        const val MAX_DAYS_DIGITS = 2
        const val NO_LENGTH = -1
        const val HALF_DAY = -2
    }
}
