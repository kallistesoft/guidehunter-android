package ca.kallistesoft.guidehunter.feature.map

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import ca.kallistesoft.guidehunter.core.auth.AccountSession
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.data.RequestRepository
import ca.kallistesoft.guidehunter.core.model.InterestRequest
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.model.UserContact
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGoogleSignIn
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeRequestRepository
import ca.kallistesoft.guidehunter.core.testing.FakeUserAccountRepository
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import ca.kallistesoft.guidehunter.core.ads.RewardOutcome
import ca.kallistesoft.guidehunter.core.model.Departure
import ca.kallistesoft.guidehunter.core.model.Discipline
import ca.kallistesoft.guidehunter.core.model.GeoBounds
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Schedule
import ca.kallistesoft.guidehunter.core.model.Slot
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.model.Trip
import ca.kallistesoft.guidehunter.core.model.TripLength
import ca.kallistesoft.guidehunter.core.testing.FakeGuideRepository
import ca.kallistesoft.guidehunter.core.testing.FakeLocationRepository
import ca.kallistesoft.guidehunter.core.testing.FakeRewardedAds
import ca.kallistesoft.guidehunter.core.testing.FakeSettingsRepository
import ca.kallistesoft.guidehunter.core.testing.MainDispatcherRule
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.testing.collecting
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val guides = FakeGuideRepository()
    private val location = FakeLocationRepository()
    private val ads = FakeRewardedAds()
    private val settings = FakeSettingsRepository()
    private val auth = FakeAuthRepository()
    private val google = FakeGoogleSignIn()
    private val users = FakeUserAccountRepository()
    private val requests = FakeRequestRepository()
    private val session = AccountSession(auth, google, FakeGuideAccountRepository(requests), users, requests, settings)
    private val activity = mockk<Context>()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        MapViewModel(guides, location, ads, settings, session, users, requests, saved, { TestGuides.today }, { TestGuides.now })

    private val me = TestGuides.user.uid

    /** What the species screen does: writes the chosen species to the settings. */
    private fun chooseSpecies(vararg species: Species) {
        settings.settings.value = settings.settings.value.copy(speciesFilter = species.toSet())
    }

    private val MapViewModel.ids get() = uiState.value.guides.map { it.guide.id }
    private val MapViewModel.contact get() = uiState.value.selected?.contact

    @Test
    fun `loading until the guides come, then every guide`() = runTest {
        val vm = viewModel()
        assertTrue(vm.uiState.value.loading)
        collecting(vm.uiState)
        assertEquals(listOf("bear", "salmon", "moose"), vm.ids)
        assertTrue(vm.uiState.value.guides.all { it.distanceKm == null })
        assertEquals("the location is read once at start (null without the permission)", 1, location.reads)
    }

    /** [count] hunting guides boosted until [until], around Abitibi (south of 49°N), and [TestGuides.all]. */
    private fun boosted(count: Int, until: kotlin.time.Instant = TestGuides.now + 2.days) =
        (1..count).map { TestGuides.bear.copy(id = "boosted-$it", location = GeoPoint(48.0, -79.0 + it * 0.1), boostedUntil = until) } +
            TestGuides.all

    private val MapViewModel.featured get() = uiState.value.featured

    @Test
    fun `boosted guides are featured, their cards first, with a badge in their sheet`() = runTest {
        guides.guides.value = boosted(2)
        val vm = viewModel()
        collecting(vm.uiState)
        assertEquals(setOf("boosted-1", "boosted-2"), vm.featured)
        assertEquals(listOf("boosted-1", "boosted-2", "bear", "salmon", "moose"), vm.uiState.value.inView.map { it.guide.id })
        vm.select("boosted-1")
        assertTrue(vm.uiState.value.selected!!.featured)
        vm.select("bear")
        assertFalse(vm.uiState.value.selected!!.featured)
    }

    @Test
    fun `an ended boost isn't featured`() = runTest {
        guides.guides.value = boosted(2, until = TestGuides.now - 1.hours) + TestGuides.moose.copy(id = "now", boostedUntil = TestGuides.now)
        val vm = viewModel()
        collecting(vm.uiState)
        assertTrue(vm.featured.isEmpty())
        assertEquals(listOf("boosted-1", "boosted-2"), vm.uiState.value.inView.take(2).map { it.guide.id })
        vm.select("boosted-1")
        assertFalse(vm.uiState.value.selected!!.featured)
    }

    @Test
    fun `at most 3 are featured, the same ones while the map pans`() = runTest {
        guides.guides.value = boosted(8)
        val saved = SavedStateHandle()
        val vm = viewModel(saved)
        collecting(vm.uiState)
        val picked = vm.featured
        assertEquals(3, picked.size)
        assertEquals("their cards first", picked, vm.uiState.value.inView.take(3).map { it.guide.id }.toSet())
        // Pan around them: the same three stay featured.
        vm.showArea(GeoBounds(GeoPoint(45.0, -80.0), GeoPoint(50.0, -70.0)))
        assertEquals(picked, vm.featured)
        vm.showArea(GeoBounds(GeoPoint(47.0, -79.5), GeoPoint(49.0, -77.0)))
        assertEquals(picked, vm.featured)
        // A new map after process death keeps the pick; another visit draws again (its own seed).
        assertEquals(picked, viewModel(saved).also { collecting(it.uiState) }.featured)
        val draws = (1..10).map { viewModel().also { other -> collecting(other.uiState) }.featured }.toSet()
        assertTrue("other visits feature others", draws.size > 1)
    }

    @Test
    fun `only the boosted guides in view are featured, and the filters still apply`() = runTest {
        guides.guides.value = boosted(1) + TestGuides.salmon.copy(id = "boosted-salmon", boostedUntil = TestGuides.now + 1.days)
        val vm = viewModel()
        collecting(vm.uiState)
        assertEquals(setOf("boosted-1", "boosted-salmon"), vm.featured)
        // West of Gaspé: the salmon is out of view.
        vm.showArea(GeoBounds(GeoPoint(45.0, -80.0), GeoPoint(50.0, -70.0)))
        assertEquals(setOf("boosted-1"), vm.featured)
        vm.chooseDiscipline(Discipline.FISHING)
        assertTrue("a boost never shows a guide the filters hide", vm.featured.isEmpty())
        assertTrue(vm.uiState.value.inView.none { it.guide.id == "boosted-1" })
    }

    @Test
    fun `the cards keep the guides on screen, the pins every guide`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        assertEquals("all of them until the map tells", vm.ids, vm.uiState.value.inView.map { it.guide.id })
        // Southern Quebec, west of Gaspé: the bear and the moose.
        vm.showArea(GeoBounds(GeoPoint(45.0, -80.0), GeoPoint(50.0, -70.0)))
        assertEquals(listOf("bear", "moose"), vm.uiState.value.inView.map { it.guide.id })
        assertEquals(listOf("bear", "salmon", "moose"), vm.ids)
        vm.chooseDiscipline(Discipline.FISHING)
        assertEquals("the filters still apply", listOf("moose"), vm.uiState.value.inView.map { it.guide.id })
    }

    @Test
    fun `filters narrow the guides, and species of the other discipline are dropped`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.chooseDiscipline(Discipline.FISHING)
        assertEquals(listOf("salmon", "moose"), vm.ids)
        chooseSpecies(Species.WALLEYE)
        assertEquals(listOf("moose"), vm.ids)
        vm.chooseDiscipline(Discipline.FISHING)
        assertEquals("same discipline keeps the species", setOf(Species.WALLEYE), vm.uiState.value.filter.species)
        vm.chooseDiscipline(Discipline.HUNTING)
        assertEquals(emptySet<Species>(), vm.uiState.value.filter.species)
        assertEquals("dropped from the settings too", emptySet<Species>(), settings.settings.value.speciesFilter)
        assertEquals(listOf("bear", "moose"), vm.ids)
        vm.chooseDiscipline(null)
        chooseSpecies(Species.MOOSE, Species.WALLEYE)
        assertEquals(listOf("moose"), vm.ids)
        vm.chooseDiscipline(null)
        assertEquals("both disciplines keep every species", setOf(Species.MOOSE, Species.WALLEYE), settings.settings.value.speciesFilter)
    }

    @Test
    fun `several species show the guides offering any of them`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        chooseSpecies(Species.BLACK_BEAR, Species.WALLEYE)
        assertEquals(listOf("bear", "moose"), vm.ids)
        chooseSpecies(Species.WALLEYE)
        assertEquals("the map follows the settings", listOf("moose"), vm.ids)
        chooseSpecies()
        assertEquals(3, vm.ids.size)
    }

    @Test
    fun `the species are a setting, kept for the next visit`() = runTest {
        chooseSpecies(Species.BLACK_BEAR, Species.ATLANTIC_SALMON)
        val vm = viewModel()
        collecting(vm.uiState)
        assertEquals(setOf(Species.BLACK_BEAR, Species.ATLANTIC_SALMON), vm.uiState.value.filter.species)
        assertEquals(listOf("bear", "salmon"), vm.ids)
    }

    @Test
    fun `the discipline survives process death`() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).chooseDiscipline(Discipline.FISHING)
        val restored = viewModel(saved)
        collecting(restored.uiState)
        assertEquals(Discipline.FISHING, restored.uiState.value.filter.discipline)
        assertEquals(listOf("salmon", "moose"), restored.ids)
    }

    @Test
    fun `with the user's location, guides show their distance, nearest first`() = runTest {
        location.location = TestGuides.quebecCity
        val vm = viewModel()
        collecting(vm.uiState)
        assertEquals("read when the screen opens", TestGuides.quebecCity, vm.uiState.value.userLocation)
        assertEquals(listOf("moose", "salmon", "bear"), vm.ids)
        val bear = vm.uiState.value.guides.first { it.guide.id == "bear" }
        assertEquals(TestGuides.bear.location.distanceKm(TestGuides.quebecCity).roundToInt(), bear.distanceKm)

        location.location = null // permission revoked: the last known place stays
        vm.refreshLocation()
        assertEquals(TestGuides.quebecCity, vm.uiState.value.userLocation)
    }

    @Test
    fun `opening a guide shows its offer with the contact locked, and gets an ad ready`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("bear")
        assertEquals(TestGuides.bear, vm.uiState.value.selected?.item?.guide)
        assertEquals(ContactState.Locked, vm.contact)
        assertEquals(1, ads.preloads)
        vm.dismiss()
        assertNull(vm.uiState.value.selected)
    }

    @Test
    fun `an ad watched to the end offers "I'm interested", and the guide stays unlocked`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("bear")
        vm.unlock()
        assertEquals(ContactState.Unlocked, vm.contact)
        assertEquals(1, ads.shown)

        vm.dismiss()
        vm.select("bear")
        assertEquals(ContactState.Unlocked, vm.contact)
        vm.unlock()
        assertEquals("no second ad", 1, ads.shown)
        assertEquals("no preload for an unlocked guide", 1, ads.preloads)
    }

    @Test
    fun `no ad to watch through no fault of the user still unlocks`() = runTest {
        ads.outcome = RewardOutcome.NO_ADS
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("moose")
        vm.unlock()
        assertEquals(ContactState.Unlocked, vm.contact)
    }

    @Test
    fun `an ad closed early or offline keeps the guide locked`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("bear")
        ads.outcome = RewardOutcome.NOT_EARNED
        vm.unlock()
        assertEquals(ContactState.Failed(UnlockError.NOT_EARNED), vm.contact)
        ads.outcome = RewardOutcome.OFFLINE
        vm.unlock()
        assertEquals(ContactState.Failed(UnlockError.OFFLINE), vm.contact)
        ads.outcome = RewardOutcome.EARNED
        vm.unlock()
        assertEquals(ContactState.Unlocked, vm.contact)
    }

    @Test
    fun `a second tap while the ad shows does nothing, and no guide open means no ad`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.unlock()
        assertEquals(0, ads.shown)
        vm.select("bear")
        ads.onShow = CompletableDeferred()
        vm.unlock()
        assertEquals(ContactState.Unlocking, vm.contact)
        vm.unlock()
        assertEquals(1, ads.shown)
        ads.onShow?.complete(Unit)
        assertEquals(ContactState.Unlocked, vm.contact)
    }

    @Test
    fun `the open guide and the unlocked ones survive process death`() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).apply {
            select("bear")
            unlock()
        }
        val restored = viewModel(saved)
        collecting(restored.uiState)
        assertEquals("bear", restored.uiState.value.selected?.item?.guide?.id)
        assertEquals("without another ad", ContactState.Unlocked, restored.contact)
        assertEquals(1, ads.shown)
    }

    // "I'm interested"

    /** Signed in, the ad watched for [id]. */
    private fun TestScope.unlocked(id: String = "bear", signedIn: Boolean = true): MapViewModel {
        if (signedIn) auth.user.value = TestGuides.user
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select(id)
        vm.unlock()
        return vm
    }

    @Test
    fun `interested, signed in with a complete contact, confirms what's sent, then sends it`() = runTest {
        users.contacts.value = mapOf(me to TestGuides.userContact.copy(email = "old@example.com"))
        val vm = unlocked()
        val trip = vm.uiState.value.selected!!.trips[1]
        vm.chooseTrip(trip)
        assertNull(vm.uiState.value.confirm)
        vm.interested(activity)
        assertEquals("no sign-in needed", 0, google.shown)
        assertEquals(
            "the account's email, always",
            RequestConfirmation("Bear hunt", trip, TestGuides.userContact.copy(email = TestGuides.user.email)),
            vm.uiState.value.confirm,
        )
        vm.sendRequest()
        val sent = requests.requests.value.getValue("bear_$me")
        assertEquals(RequestStatus.PENDING, sent.status)
        assertEquals(trip, sent.trip)
        assertEquals(TestGuides.OWNER, sent.guideUid)
        assertNull(vm.uiState.value.confirm)
        assertTrue(vm.uiState.value.sent)
        assertEquals(ContactState.Requested(sent), vm.contact)
        vm.sentShown()
        assertFalse(vm.uiState.value.sent)
    }

    @Test
    fun `signed out, interested signs in first, closing Google's sheet keeps the ad watched`() = runTest {
        val vm = unlocked(signedIn = false)
        google.token = null
        vm.interested(activity)
        assertEquals(1, google.shown)
        assertEquals(ContactState.Unlocked, vm.contact)
        assertNull(vm.uiState.value.confirm)

        google.token = FakeAuthRepository.TOKEN
        vm.interested(activity)
        assertEquals(TestGuides.user, auth.user.value)
        assertEquals("the Google name until the user saves a contact", TestGuides.user.name, vm.uiState.value.confirm?.contact?.name)
    }

    @Test
    fun `a failed sign-in says so, and the next try clears it`() = runTest {
        val vm = unlocked(signedIn = false)
        google.failure = IllegalStateException("offline")
        vm.interested(activity)
        assertEquals(RequestError.SIGN_IN_FAILED, vm.uiState.value.selected?.error)
        assertEquals(ContactState.Unlocked, vm.contact)
        google.failure = BackendNotConfiguredException()
        vm.interested(activity)
        assertEquals(RequestError.NOT_CONFIGURED, vm.uiState.value.selected?.error)
    }

    @Test
    fun `an incomplete contact opens the contact form once, then the confirmation once it's filled in`() = runTest {
        auth.user.value = TestGuides.user.copy(name = "")
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("bear")
        vm.unlock()
        vm.interested(activity)
        assertTrue(vm.uiState.value.askContact)
        assertNull(vm.uiState.value.confirm)
        vm.contactAsked()
        assertFalse("not asked again", vm.uiState.value.askContact)
        users.saveContact(me, UserContact("Jo", "x", ""))
        assertEquals(UserContact("Jo", TestGuides.user.email, ""), vm.uiState.value.confirm?.contact)
        vm.dismissConfirm()
        assertNull(vm.uiState.value.confirm)
    }

    @Test
    fun `a request that fails to send says so and can be sent again, a second tap while it goes does nothing`() = runTest {
        val vm = unlocked()
        vm.interested(activity)
        requests.failure = IllegalStateException("offline")
        vm.sendRequest()
        assertEquals(RequestError.SEND_FAILED, vm.uiState.value.selected?.error)
        assertTrue("still confirming", vm.uiState.value.confirm != null)
        requests.failure = null
        requests.onWrite = CompletableDeferred()
        vm.sendRequest()
        assertTrue(vm.uiState.value.selected!!.busy)
        vm.sendRequest()
        vm.cancelRequest()
        requests.onWrite?.complete(Unit)
        assertEquals(RequestStatus.PENDING, (vm.contact as ContactState.Requested).request.status)
        assertNull(vm.uiState.value.selected?.error)
    }

    @Test
    fun `an offer already asked about shows its request with no ad, even after a relaunch`() = runTest {
        requests.give(TestGuides.request)
        auth.user.value = TestGuides.user
        val saved = SavedStateHandle()
        viewModel(saved).apply {
            collecting(uiState)
            select("bear")
            assertEquals(ContactState.Requested(TestGuides.request), uiState.value.selected?.contact)
        }
        val relaunched = viewModel(saved)
        collecting(relaunched.uiState)
        assertEquals(ContactState.Requested(TestGuides.request), relaunched.contact)
        assertEquals(0, ads.shown)
    }

    @Test
    fun `the request is read before anything shows`() = runTest {
        auth.user.value = TestGuides.user
        val reading = MutableSharedFlow<InterestRequest?>()
        val slow = object : RequestRepository by requests {
            override fun request(offerId: String, uid: String) = reading
        }
        val vm = MapViewModel(guides, location, ads, settings, session, users, slow, SavedStateHandle(), { TestGuides.today }, { TestGuides.now })
        collecting(vm.uiState)
        vm.select("bear")
        assertEquals(ContactState.Loading, vm.contact)
        reading.emit(null)
        assertEquals(ContactState.Locked, vm.contact)
    }

    @Test
    fun `a pending request can be cancelled, then sent again with the contact as it is now`() = runTest {
        requests.give(TestGuides.request)
        users.contacts.value = mapOf(me to TestGuides.userContact.copy(phone = ""))
        auth.user.value = TestGuides.user
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("bear")
        vm.cancelRequest()
        assertEquals(RequestStatus.CANCELLED, requests.requests.value.getValue(TestGuides.request.id).status)
        vm.interested(activity)
        assertEquals("", vm.uiState.value.confirm?.contact?.phone)
        vm.sendRequest()
        val again = requests.requests.value.getValue(TestGuides.request.id)
        assertEquals(RequestStatus.PENDING, again.status)
        assertEquals("", again.user.phone)
        assertEquals(0, ads.shown)
    }

    @Test
    fun `a failed cancel says so, without a request there's nothing to cancel or send`() = runTest {
        requests.give(TestGuides.request)
        auth.user.value = TestGuides.user
        val vm = viewModel()
        collecting(vm.uiState)
        vm.sendRequest()
        vm.interested(activity)
        vm.select("bear")
        requests.failure = IllegalStateException("offline")
        vm.cancelRequest()
        assertEquals(RequestError.SEND_FAILED, vm.uiState.value.selected?.error)
        vm.select("moose")
        assertNull("another offer starts afresh", vm.uiState.value.selected?.error)
        vm.cancelRequest()
        vm.sendRequest()
        assertEquals(1, requests.requests.value.size)
    }

    @Test
    fun `a pending or accepted request can't be confirmed again`() = runTest {
        requests.give(TestGuides.request)
        auth.user.value = TestGuides.user
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("bear")
        vm.interested(activity)
        assertNull(vm.uiState.value.confirm)
        assertFalse(vm.uiState.value.askContact)
    }

    @Test
    fun `the account's own offer has no ad and no request`() = runTest {
        auth.user.value = TestGuides.user.copy(uid = TestGuides.OWNER)
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("bear")
        assertEquals(ContactState.Own, vm.contact)
    }

    @Test
    fun `a selected guide that's no longer published closes`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("bear")
        guides.guides.value = listOf(TestGuides.salmon)
        assertNull(vm.uiState.value.selected)
    }

    private fun day(month: Int, day: Int) = LocalDate(2027, month, day)

    @Test
    fun `the dates show the guides with a trip that fits them, and those on request`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.chooseDates(Trip(day(5, 14), day(5, 31)))
        assertEquals("bear's week of May 15 fits, salmon's June half day doesn't", listOf("bear", "moose"), vm.ids)
        vm.chooseDates(Trip(day(5, 18), day(5, 31)))
        assertEquals("a week leaving May 15 starts too early", listOf("moose"), vm.ids)
        vm.chooseDates(Trip(day(7, 2)))
        assertEquals("only moose, on request", listOf("moose"), vm.ids)
        vm.chooseDiscipline(Discipline.HUNTING)
        assertEquals("the discipline keeps the dates", Trip(day(7, 2)), vm.uiState.value.filter.dates)
        vm.clearDates()
        assertEquals(listOf("bear", "moose"), vm.ids)
        assertEquals(TestGuides.today, vm.uiState.value.today)
    }

    @Test
    fun `an offer whose dates are over leaves the map`() = runTest {
        val past = TestGuides.bear.copy(schedule = Schedule(TripLength.Days(7), listOf(Departure(day(3, 1)))))
        guides.guides.value = listOf(past, TestGuides.moose)
        val vm = viewModel()
        collecting(vm.uiState)
        assertEquals(listOf("moose"), vm.ids)
    }

    @Test
    fun `an open guide lists its trips, the user picks one, and the dates pick the first that fits`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("bear")
        val selected = vm.uiState.value.selected!!
        assertEquals(listOf(day(5, 1), day(5, 15), day(6, 5)), selected.trips.map { it.start })
        assertNull(selected.trip)
        vm.chooseTrip(selected.trips[1])
        assertEquals(Trip(day(5, 15), day(5, 21)), vm.uiState.value.selected!!.trip)
        vm.chooseTrip(selected.trips[1])
        assertNull("again: unchosen", vm.uiState.value.selected!!.trip)
        vm.chooseDates(Trip(day(6, 1), day(6, 30)))
        assertEquals(day(6, 5), vm.uiState.value.selected!!.trip!!.start)
        vm.select("moose")
        assertEquals(emptyList<Trip>(), vm.uiState.value.selected!!.trips)
        assertNull(vm.uiState.value.selected!!.trip)
    }

    @Test
    fun `the dates and the chosen trip survive process death`() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).apply {
            chooseDates(Trip(day(6, 1), day(6, 30)))
            select("salmon")
            chooseTrip(Trip(day(6, 10), slot = Slot.AFTERNOON))
        }
        val restored = viewModel(saved)
        collecting(restored.uiState)
        assertEquals(Trip(day(6, 1), day(6, 30)), restored.uiState.value.filter.dates)
        assertEquals(Trip(day(6, 10), slot = Slot.AFTERNOON), restored.uiState.value.selected!!.trip)
        restored.select("salmon")
        assertEquals("opening the same guide keeps the trip", Slot.AFTERNOON, restored.uiState.value.selected!!.trip!!.slot)
        restored.dismiss()
        assertNull(restored.uiState.value.selected)
    }
}
