package ca.kallistesoft.guidehunter.feature.map

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kallistesoft.guidehunter.core.ads.RewardOutcome
import ca.kallistesoft.guidehunter.core.ads.RewardedAds
import ca.kallistesoft.guidehunter.core.auth.AccountSession
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.data.GuideRepository
import ca.kallistesoft.guidehunter.core.data.RequestRepository
import ca.kallistesoft.guidehunter.core.data.UserAccountRepository
import ca.kallistesoft.guidehunter.core.data.LocationRepository
import ca.kallistesoft.guidehunter.core.data.SettingsRepository
import ca.kallistesoft.guidehunter.core.model.Boost
import ca.kallistesoft.guidehunter.core.model.Discipline
import ca.kallistesoft.guidehunter.core.model.GeoBounds
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideFilter
import ca.kallistesoft.guidehunter.core.model.Now
import ca.kallistesoft.guidehunter.core.model.GuideUser
import ca.kallistesoft.guidehunter.core.model.InterestRequest
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.model.UserContact
import ca.kallistesoft.guidehunter.core.model.Slot
import ca.kallistesoft.guidehunter.core.model.Today
import ca.kallistesoft.guidehunter.core.model.Trip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
import kotlin.random.Random
import kotlinx.datetime.LocalDate

/** A guide on the map, and how far it is from the user (null while the user's location is unknown). */
data class GuideItem(val guide: Guide, val distanceKm: Int? = null)

/** How the user can reach the open offer's guide: the ad, then "I'm interested", or the request they sent. */
sealed interface ContactState {
    /** Before the rewarded ad. */
    data object Locked : ContactState

    /** The ad is showing. */
    data object Unlocking : ContactState

    data class Failed(val reason: UnlockError) : ContactState

    /** The ad was watched (this session): "I'm interested". */
    data object Unlocked : ContactState

    /** The signed-in account's own offer: no request. */
    data object Own : ContactState

    /** The user's request for this offer is being read. */
    data object Loading : ContactState

    /** The user sent a request for this offer: its status, no ad (even after a relaunch). */
    data class Requested(val request: InterestRequest) : ContactState
}

enum class UnlockError {
    /** The ad was closed before the end. */
    NOT_EARNED,

    /** No network: no ad. */
    OFFLINE,
}

/** Why "I'm interested" (or cancelling) didn't go through, said in the offer until the next try. */
enum class RequestError { SIGN_IN_FAILED, SEND_FAILED, NOT_CONFIGURED }

/**
 * The guide whose offer is open: its [trips] still to come (soonest first), and the [trip] the user chose, which the
 * request repeats. [busy] while signing in or sending; [error] when that failed.
 */
data class SelectedGuide(
    val item: GuideItem,
    val contact: ContactState,
    val trips: List<Trip> = emptyList(),
    val trip: Trip? = null,
    /** Boosted by its guide: the sheet says it's featured. */
    val featured: Boolean = false,
    val busy: Boolean = false,
    val error: RequestError? = null,
)

/** "Send your request?": what the guide will get. */
data class RequestConfirmation(val offerTitle: String, val trip: Trip?, val contact: UserContact)

data class MapUiState(
    val loading: Boolean = true,
    /** The guides that match [filter], nearest first once the user's location is known. */
    val guides: List<GuideItem> = emptyList(),
    /** The [guides] in the part of the map on screen (all of them until the map tells): the cards, [featured] first. */
    val inView: List<GuideItem> = emptyList(),
    /**
     * The ids of the boosted guides in view that are featured: at most [Boost.MAX_FEATURED], picked at random when
     * more are boosted (the same pick while the map pans). Their pins are on top, their cards first.
     */
    val featured: Set<String> = emptySet(),
    val filter: GuideFilter = GuideFilter(),
    val userLocation: GeoPoint? = null,
    /** The guide whose offer is open, or null. */
    val selected: SelectedGuide? = null,
    /** The first day the dates picker offers. */
    val today: LocalDate = LocalDate.fromEpochDays(0),
    /** "I'm interested" tapped, signed in, contact complete: confirm what's sent. */
    val confirm: RequestConfirmation? = null,
    /** "I'm interested" tapped, but the user's contact isn't complete: open it (once). */
    val askContact: Boolean = false,
    /** A request just went: say so, with a way to "My requests". */
    val sent: Boolean = false,
)

/**
 * The map of guides: filtered by discipline, species and the dates the user can go, with the distance from the user.
 * The species are a setting ([SettingsRepository], chosen on their own screen and kept for next time); the discipline
 * and the dates are this screen's. Opening a guide shows its public offer; after a rewarded ad the user can say
 * "I'm interested" (signing in and filling in their contact if needed, then confirming), which sends the guide a
 * request; an offer they already asked about shows its request instead (Pattern A of .claude/skills/viewmodel-state).
 * Boosted guides among those shown are featured ([MapUiState.featured]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val guideRepository: GuideRepository,
    private val locationRepository: LocationRepository,
    private val rewardedAds: RewardedAds,
    private val settings: SettingsRepository,
    private val session: AccountSession,
    private val userAccount: UserAccountRepository,
    private val requests: RequestRepository,
    private val savedState: SavedStateHandle,
    private val today: Today,
    private val now: Now,
) : ViewModel() {
    /**
     * Which boosted guides are featured when more are in view than can be: drawn once per map, kept while it pans
     * (and over process death), a new draw on the next visit.
     */
    private val featureSeed: Long = savedState[KEY_SEED] ?: Random.nextLong().also { savedState[KEY_SEED] = it }

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

    /** The ad showing, or how it failed, by guide. */
    private val ads = MutableStateFlow<Map<String, ContactState>>(emptyMap())

    /** "I'm interested" in progress: signing in or sending, its error, the offer being confirmed, a request sent. */
    private data class Work(
        val busy: Boolean = false,
        val error: RequestError? = null,
        val confirming: String? = null,
        val contactAsked: Boolean = false,
        val sent: Boolean = false,
    )

    private val work = MutableStateFlow(Work())

    /** The signed-in user's request for the open offer: Loading until read, null if none; nothing when signed out. */
    private sealed interface Lookup {
        data object None : Lookup
        data object Loading : Lookup
        data class Read(val request: InterestRequest?) : Lookup
    }

    private val openId = selection.map { it.id }.distinctUntilChanged()

    private val lookup: Flow<Lookup> = combine(session.user, openId) { user, id -> user to id }
        .flatMapLatest { (user, id) ->
            if (user == null || id == null) flowOf(Lookup.None) else requests.request(id, user.uid).map<InterestRequest?, Lookup> { Lookup.Read(it) }.onStart { emit(Lookup.Loading) }
        }

    /** What the user would share: their saved contact (or their Google name), always with the account's email. */
    private val userContact: Flow<UserContact?> = session.user.flatMapLatest { user ->
        if (user == null) {
            flowOf(null)
        } else {
            userAccount.contact(user.uid).map { saved -> (saved ?: UserContact(name = user.name)).copy(email = user.email) }
        }
    }

    private data class Access(
        val user: GuideUser?,
        val lookup: Lookup,
        val contact: UserContact?,
        val unlocked: Set<String>,
        val ads: Map<String, ContactState>,
    )

    private val access = combine(session.user, lookup, userContact, unlocked, ads, ::Access)

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

    val uiState: StateFlow<MapUiState> = combine(listing, visibleArea, selection, access, work) { listing, area, selection, access, work ->
        val time = now()
        val inArea = if (area == null) listing.shown else listing.shown.filter { it.guide.location in area }
        // The filters apply first: a boost never shows a guide that doesn't match them.
        val featured = Boost.featured(inArea.map { it.guide }.filter { it.isBoosted(time) }, featureSeed).map { it.id }.toSet()
        val selected = listing.all.find { it.guide.id == selection.id }?.let { item ->
            val trips = item.guide.schedule.trips(today())
            SelectedGuide(
                item = item,
                contact = contactState(item.guide, access),
                trips = trips,
                // Searching dates: the first trip that fits them, until the user picks another.
                trip = selection.trip?.takeIf { it in trips } ?: listing.filter.dates?.let { free -> trips.find { it.within(free) } },
                featured = item.guide.isBoosted(time),
                busy = work.busy,
                error = work.error,
            )
        }
        val confirming = selected != null && work.confirming == selected.item.guide.id && selected.contact.canRequest
        val contact = access.contact
        MapUiState(
            loading = false,
            guides = listing.shown,
            inView = inArea.sortedByDescending { it.guide.id in featured },
            featured = featured,
            filter = listing.filter,
            userLocation = listing.location,
            selected = selected,
            today = today(),
            confirm = if (confirming && contact?.isComplete == true) RequestConfirmation(selected.item.guide.title, selected.trip, contact) else null,
            askContact = confirming && contact != null && !contact.isComplete && !work.contactAsked,
            sent = work.sent,
        )
    }.onStart {
        // The distances show at once when the location permission was given before (null without it).
        if (userLocation.value == null) refreshLocation()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    private data class Listing(val all: List<GuideItem>, val shown: List<GuideItem>, val filter: GuideFilter, val location: GeoPoint?)

    /** Own offer first, then the user's request (none: the ad, once per guide and session). */
    private fun contactState(guide: Guide, access: Access): ContactState {
        val user = access.user
        if (user != null && guide.ownerUid.isNotEmpty() && guide.ownerUid == user.uid) return ContactState.Own
        when (val lookup = access.lookup) {
            Lookup.Loading -> return ContactState.Loading
            is Lookup.Read -> lookup.request?.let { return ContactState.Requested(it) }
            Lookup.None -> Unit
        }
        if (guide.id in access.unlocked) return ContactState.Unlocked
        return access.ads[guide.id] ?: ContactState.Locked
    }

    /** "I'm interested" leads to a request: after the ad, or for a cancelled one ("Send again"). */
    private val ContactState.canRequest: Boolean
        get() = this == ContactState.Unlocked || (this is ContactState.Requested && request.status == RequestStatus.CANCELLED)
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

    /** Opens a guide's offer, and gets a rewarded ad ready for it. */
    fun select(guideId: String) {
        if (selection.value.id != guideId) work.value = Work()
        setSelection { if (it.id == guideId) it else Selection(guideId) }
        if (guideId !in unlocked.value) rewardedAds.preload()
    }

    fun dismiss() {
        work.value = Work()
        setSelection { Selection() }
    }

    /** "Watch an ad to contact the guide": shows the rewarded ad, then "I'm interested" if it was watched to the end. */
    fun unlock() {
        val id = selection.value.id ?: return
        if (ads.value[id] == ContactState.Unlocking || id in unlocked.value) return
        setAd(id, ContactState.Unlocking)
        viewModelScope.launch {
            when (rewardedAds.showForReward()) {
                RewardOutcome.EARNED, RewardOutcome.NO_ADS -> {
                    unlocked.update { it + id }
                    savedState[KEY_UNLOCKED] = unlocked.value.toTypedArray()
                    ads.update { it - id }
                }
                RewardOutcome.NOT_EARNED -> setAd(id, ContactState.Failed(UnlockError.NOT_EARNED))
                RewardOutcome.OFFLINE -> setAd(id, ContactState.Failed(UnlockError.OFFLINE))
            }
        }
    }

    /**
     * "I'm interested" (or "Send again"): signs in first if needed (Google's sheet over [activity]; closed, the ad
     * stays watched), then the confirmation shows, or the contact form if the user's contact isn't complete.
     */
    fun interested(activity: Context) {
        val id = selection.value.id ?: return
        run(RequestError.SIGN_IN_FAILED) {
            if (session.user.first() == null && !session.signIn(activity)) return@run
            work.update { it.copy(confirming = id, contactAsked = false) }
        }
    }

    /** The contact form opened: it isn't asked again until the next "I'm interested". */
    fun contactAsked() = work.update { it.copy(contactAsked = true) }

    /** "Not now". */
    fun dismissConfirm() = work.update { it.copy(confirming = null) }

    /** Sends the confirmed request (again, for a cancelled one). */
    fun sendRequest() {
        val state = uiState.value
        val confirm = state.confirm ?: return
        val selected = state.selected ?: return
        run(RequestError.SEND_FAILED) {
            val user = checkNotNull(session.user.first()) { "Signed out" }
            val existing = (selected.contact as? ContactState.Requested)?.request
            if (existing != null) {
                requests.resend(existing.id, confirm.trip, confirm.contact)
            } else {
                requests.send(user.uid, selected.item.guide, confirm.trip, confirm.contact)
            }
            work.update { it.copy(confirming = null, sent = true) }
        }
    }

    /** The user cancels their request for the open offer. */
    fun cancelRequest() {
        val request = (uiState.value.selected?.contact as? ContactState.Requested)?.request ?: return
        run(RequestError.SEND_FAILED) { requests.cancel(request.id) }
    }

    /** "Request sent" was shown. */
    fun sentShown() = work.update { it.copy(sent = false) }

    /** One action at a time: a second tap while one runs does nothing; a failure shows until the next try. */
    private fun run(failure: RequestError, action: suspend () -> Unit) {
        if (work.value.busy) return
        work.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val error = try {
                action()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (_: BackendNotConfiguredException) {
                RequestError.NOT_CONFIGURED
            } catch (_: Exception) {
                failure
            }
            work.update { it.copy(busy = false, error = error) }
        }
    }

    /** Reads the user's location (the permission was just granted, or the screen opened with it). */
    fun refreshLocation() {
        viewModelScope.launch { locationRepository.current()?.let { userLocation.value = it } }
    }

    private fun setAd(id: String, state: ContactState) = ads.update { it + (id to state) }

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
        const val KEY_SEED = "featureSeed"
    }
}
