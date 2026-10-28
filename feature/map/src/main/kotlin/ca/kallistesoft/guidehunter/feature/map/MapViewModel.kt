package ca.kallistesoft.guidehunter.feature.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kallistesoft.guidehunter.core.ads.RewardOutcome
import ca.kallistesoft.guidehunter.core.ads.RewardedAds
import ca.kallistesoft.guidehunter.core.data.GuideRepository
import ca.kallistesoft.guidehunter.core.data.LocationRepository
import ca.kallistesoft.guidehunter.core.data.SettingsRepository
import ca.kallistesoft.guidehunter.core.model.Discipline
import ca.kallistesoft.guidehunter.core.model.GeoBounds
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.GuideFilter
import ca.kallistesoft.guidehunter.core.model.Slot
import ca.kallistesoft.guidehunter.core.model.Today
import ca.kallistesoft.guidehunter.core.model.Trip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate

/** A guide on the map, and how far it is from the user (null while the user's location is unknown). */
data class GuideItem(val guide: Guide, val distanceKm: Int? = null)

/** Where the guide's name and contact details stand for the user. */
sealed interface ContactState {
    /** Hidden until the user watches a rewarded ad. */
    data object Locked : ContactState

    /** The ad is showing, or the details are loading. */
    data object Unlocking : ContactState

    data class Unlocked(val contact: GuideContact) : ContactState

    /** Earned, but the guide has no contact details yet. */
    data object Missing : ContactState

    data class Failed(val reason: UnlockError) : ContactState
}

enum class UnlockError {
    /** The ad was closed before the end. */
    NOT_EARNED,

    /** No network: no ad, no contact. */
    OFFLINE,

    /** The ad was watched but the details couldn't be read: trying again won't show another ad. */
    LOAD_FAILED,
}

/**
 * The guide whose offer is open: its [trips] still to come (soonest first), and the [trip] the user chose, which the
 * contact section and the email to the guide repeat.
 */
data class SelectedGuide(
    val item: GuideItem,
    val contact: ContactState,
    val trips: List<Trip> = emptyList(),
    val trip: Trip? = null,
)

data class MapUiState(
    val loading: Boolean = true,
    /** The guides that match [filter], nearest first once the user's location is known. */
    val guides: List<GuideItem> = emptyList(),
    /** The [guides] in the part of the map on screen (all of them until the map tells): the cards. */
    val inView: List<GuideItem> = emptyList(),
    val filter: GuideFilter = GuideFilter(),
    val userLocation: GeoPoint? = null,
    /** The guide whose offer is open, or null. */
    val selected: SelectedGuide? = null,
    /** The first day the dates picker offers. */
    val today: LocalDate = LocalDate.fromEpochDays(0),
)

/**
 * The map of guides: filtered by discipline, species and the dates the user can go, with the distance from the user.
 * The species are a setting ([SettingsRepository], chosen on their own screen and kept for next time); the discipline
 * and the dates are this screen's. Opening a guide shows its public offer; its name and contact details are shown once the user has watched a rewarded ad (Pattern A of
 * .claude/skills/viewmodel-state).
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val guideRepository: GuideRepository,
    private val locationRepository: LocationRepository,
    private val rewardedAds: RewardedAds,
    private val settings: SettingsRepository,
    private val savedState: SavedStateHandle,
    private val today: Today,
) : ViewModel() {
    /** The screen's own choices: the discipline and the dates (the species come from the settings). */
    private val choices = MutableStateFlow(
        GuideFilter(
            discipline = savedState.get<String>(KEY_DISCIPLINE)?.let(Discipline::valueOf),
            dates = savedState.get<LongArray>(KEY_DATES)?.toTrip(),
        ),
    )

    private val filter = combine(choices, settings.settings.map { it.speciesFilter }.distinctUntilChanged()) { choices, species ->
        choices.copy(species = species)
    }

    /** The open guide, and the trip the user chose in it. */
    private data class Selection(val id: String? = null, val trip: Trip? = null)

    private val selection = MutableStateFlow(Selection(savedState.get<String>(KEY_SELECTED), savedState.get<LongArray>(KEY_TRIP)?.toTrip()))
    private val userLocation = MutableStateFlow<GeoPoint?>(null)
    private val visibleArea = MutableStateFlow<GeoBounds?>(null)

    /** The guides whose ad the user has watched (this session): they never pay twice. */
    private val unlocked = MutableStateFlow(savedState.get<Array<String>>(KEY_UNLOCKED).orEmpty().toSet())
    private val contacts = MutableStateFlow<Map<String, ContactState>>(emptyMap())

    /**
     * Every guide with its distance, and the ones the filter shows (nearest first). Computed only when the guides,
     * the filter or the location change: opening a guide doesn't rebuild the list, so the map's markers and the
     * cards don't redraw. (A few dozen guides: cheap enough for the main thread.)
     */
    private val listing = combine(guideRepository.guides, filter, userLocation) { guides, filter, location ->
        val items = guides.map { guide ->
            GuideItem(guide, location?.let { guide.location.distanceKm(it).roundToInt() })
        }
        val day = today()
        Listing(items, items.filter { filter.matches(it.guide, day) }.sortedBy { it.distanceKm ?: 0 }, filter, location)
    }.distinctUntilChanged()

    val uiState: StateFlow<MapUiState> = combine(
        listing,
        visibleArea,
        selection,
        contacts,
    ) { listing, area, selection, contacts ->
        MapUiState(
            loading = false,
            guides = listing.shown,
            inView = if (area == null) listing.shown else listing.shown.filter { it.guide.location in area },
            filter = listing.filter,
            userLocation = listing.location,
            selected = listing.all.find { it.guide.id == selection.id }?.let { item ->
                val trips = item.guide.schedule.trips(today())
                SelectedGuide(
                    item = item,
                    contact = contacts[item.guide.id] ?: ContactState.Locked,
                    trips = trips,
                    // Searching dates: the first trip that fits them, until the user picks another.
                    trip = selection.trip?.takeIf { it in trips } ?: listing.filter.dates?.let { free -> trips.find { it.within(free) } },
                )
            },
            today = today(),
        )
    }.onStart {
        reloadRestoredContact()
        // The distances show at once when the location permission was given before (null without it).
        if (userLocation.value == null) refreshLocation()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    private data class Listing(val all: List<GuideItem>, val shown: List<GuideItem>, val filter: GuideFilter, val location: GeoPoint?)

    /** The map stopped moving over [area]: the cards follow it. */
    fun showArea(area: GeoBounds) {
        visibleArea.value = area
    }

    /** Shows the guides of [discipline] (null: both). The chosen species of the other discipline are dropped. */
    fun chooseDiscipline(discipline: Discipline?) {
        setFilter { it.copy(discipline = discipline) }
        if (discipline == null) return
        viewModelScope.launch {
            val species = settings.settings.first().speciesFilter
            val kept = species.filter { it.discipline == discipline }.toSet()
            if (kept != species) settings.setSpeciesFilter(kept)
        }
    }

    /** Shows the guides with a trip that fits [free], the dates the user can go. */
    fun chooseDates(free: Trip) = setFilter { it.copy(dates = free) }

    /** Any dates again. */
    fun clearDates() = setFilter { it.copy(dates = null) }

    /** Chooses one of the open guide's trips (again: unchooses it); the contact and the email repeat it. */
    fun chooseTrip(trip: Trip) = setSelection { current -> current.copy(trip = trip.takeIf { trip != current.trip }) }

    /** Opens a guide's offer, and gets a rewarded ad ready for its contact details. */
    fun select(guideId: String) {
        setSelection { if (it.id == guideId) it else Selection(guideId) }
        if (guideId in unlocked.value) {
            // Unlocked before: read the contact unless it's there or already on its way (no second ad, ever).
            val contact = contacts.value[guideId]
            if (contact !is ContactState.Unlocked && contact != ContactState.Unlocking) viewModelScope.launch { load(guideId) }
        } else {
            rewardedAds.preload()
        }
    }

    fun dismiss() = setSelection { Selection() }

    /** "Watch an ad to see the contact": shows the rewarded ad, then the details if it was watched to the end. */
    fun unlock() {
        val id = selection.value.id ?: return
        if (contacts.value[id] == ContactState.Unlocking) return
        setContact(id, ContactState.Unlocking)
        viewModelScope.launch {
            if (id in unlocked.value) return@launch load(id)
            when (rewardedAds.showForReward()) {
                RewardOutcome.EARNED, RewardOutcome.NO_ADS -> {
                    unlocked.update { it + id }
                    savedState[KEY_UNLOCKED] = unlocked.value.toTypedArray()
                    load(id)
                }
                RewardOutcome.NOT_EARNED -> setContact(id, ContactState.Failed(UnlockError.NOT_EARNED))
                RewardOutcome.OFFLINE -> setContact(id, ContactState.Failed(UnlockError.OFFLINE))
            }
        }
    }

    /** Reads the user's location (the permission was just granted, or the screen opened with it). */
    fun refreshLocation() {
        viewModelScope.launch { locationRepository.current()?.let { userLocation.value = it } }
    }

    /** After process death: the open guide was unlocked, but its contact (not saved) has to be read again. */
    private fun reloadRestoredContact() {
        val id = selection.value.id ?: return
        if (id in unlocked.value && id !in contacts.value) {
            setContact(id, ContactState.Unlocking)
            viewModelScope.launch { load(id) }
        }
    }

    private suspend fun load(id: String) {
        setContact(id, ContactState.Unlocking)
        val state = try {
            guideRepository.contact(id)?.let(ContactState::Unlocked) ?: ContactState.Missing
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ContactState.Failed(UnlockError.LOAD_FAILED)
        }
        setContact(id, state)
    }

    private fun setContact(id: String, state: ContactState) = contacts.update { it + (id to state) }

    private fun setFilter(change: (GuideFilter) -> GuideFilter) {
        val next = choices.updateAndGet(change)
        savedState[KEY_DISCIPLINE] = next.discipline?.name
        savedState[KEY_DATES] = next.dates?.toLongs()
    }

    private fun setSelection(change: (Selection) -> Selection) {
        val next = selection.updateAndGet(change)
        savedState[KEY_SELECTED] = next.id
        savedState[KEY_TRIP] = next.trip?.toLongs()
    }

    /** A trip saved as its days (and its half of the day, -1 for none). */
    private fun Trip.toLongs() = longArrayOf(start.toEpochDays(), end.toEpochDays(), slot?.ordinal?.toLong() ?: -1)

    private fun LongArray.toTrip() = Trip(
        LocalDate.fromEpochDays(this[0]),
        LocalDate.fromEpochDays(this[1]),
        Slot.entries.getOrNull(this.getOrElse(2) { -1 }.toInt()),
    )

    private companion object {
        const val KEY_DISCIPLINE = "discipline"
        const val KEY_SELECTED = "selected"
        const val KEY_UNLOCKED = "unlocked"
        const val KEY_DATES = "dates"
        const val KEY_TRIP = "trip"
    }
}
