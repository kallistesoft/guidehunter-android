package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.lifecycle.SavedStateHandle
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.model.Departure
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import ca.kallistesoft.guidehunter.core.model.OfferField
import ca.kallistesoft.guidehunter.core.model.OfferStatus
import ca.kallistesoft.guidehunter.core.model.OfferStep
import ca.kallistesoft.guidehunter.core.model.Slot
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.model.TripLength
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeLocationRepository
import ca.kallistesoft.guidehunter.core.testing.MainDispatcherRule
import ca.kallistesoft.guidehunter.core.testing.FakeRequestRepository
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.testing.collecting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OfferEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val uid = TestGuides.user.uid
    private val auth = FakeAuthRepository(TestGuides.user)
    private val requests = FakeRequestRepository()
    private val account = FakeGuideAccountRepository(requests).apply { profiles.value = mapOf(uid to TestGuides.profile) }
    private val location = FakeLocationRepository()
    private val live = GuideOffer("live", TestGuides.offer, OfferStatus.PUBLISHED, live = true)

    private fun viewModel(offerId: String? = null, saved: SavedStateHandle = SavedStateHandle()) =
        OfferEditorViewModel(offerId, auth, account, location, requests, saved, { TestGuides.today })

    /** Types [offer] into the form, field by field, as the guide would. */
    private fun OfferEditorViewModel.fill(offer: OfferDraft = TestGuides.offer) {
        setTitle(offer.title)
        setDescription(offer.description)
        offer.activity?.let(::setActivity)
        offer.species.forEach(::toggleSpecies)
        setRegion(offer.region)
        offer.location?.let(::setLocation)
        offer.schedule.length?.let(::setLength)
        addDepartures(offer.schedule.departures.map { it.date }, offer.schedule.departures.mapNotNull { it.slot }.toSet())
        setEveryYear(offer.schedule.everyYear)
        setPrice(offer.priceFrom)
    }

    private val OfferEditorViewModel.state get() = uiState.value

    @Test
    fun `a new offer is sent for review as typed`() = runTest {
        val vm = viewModel()
        assertTrue("loads when collected", vm.state.loading)
        collecting(vm.uiState)
        assertFalse(vm.state.loading)
        assertFalse(vm.state.editing)
        vm.fill()
        assertEquals(TestGuides.offer, vm.state.form)
        vm.submit()
        assertEquals(SendState.SENT, vm.state.send)
        assertEquals(listOf(GuideOffer("offer-1", TestGuides.offer)), account.offersByUser.value[uid])
        assertEquals(listOf("offer-1" to false), account.mails)
        vm.submit()
        assertEquals("sent once", 1, account.mails.size)
    }

    @Test
    fun `an offer on the map opens with its text, and an edit keeps it on the map`() = runTest {
        account.give(uid, live)
        val vm = viewModel("live")
        collecting(vm.uiState)
        assertTrue(vm.state.editing)
        assertTrue(vm.state.live)
        assertEquals(TestGuides.offer, vm.state.form)
        vm.setPrice("2600")
        vm.submit()
        assertEquals(SendState.SENT, vm.state.send)
        val saved = account.offersByUser.value.getValue(uid).single()
        assertEquals(GuideOffer("live", TestGuides.offer.copy(priceFrom = "2600"), OfferStatus.PENDING, live = true), saved)
        assertEquals(listOf("live" to true), account.mails)
    }

    @Test
    fun `an offer deleted elsewhere, or unreadable, closes the editor`() = runTest {
        val vm = viewModel("gone")
        collecting(vm.uiState)
        assertTrue(vm.state.deleted)

        account.give(uid, live)
        account.readFailure = IllegalStateException("offline")
        val offline = viewModel("live")
        collecting(offline.uiState)
        assertTrue(offline.state.deleted)
    }

    @Test
    fun `an offer is deleted, and taken off the map, closing its open requests`() = runTest {
        account.give(uid, live)
        val pending = TestGuides.request.copy(id = "live_u2", offerId = "live", guideUid = uid, userUid = "u2")
        val other = pending.copy(id = "other_u2", offerId = "other")
        val answered = pending.copy(id = "live_u3", userUid = "u3", status = RequestStatus.REJECTED)
        requests.give(pending, other, answered)
        val vm = viewModel("live")
        collecting(vm.uiState)
        assertEquals("only this offer's pending ones", 1, vm.state.pendingRequests)
        vm.delete()
        assertTrue(vm.state.deleted)
        assertEquals(emptyList<GuideOffer>(), account.offersByUser.value[uid])
        assertEquals(RequestStatus.OFFER_REMOVED, requests.requests.value.getValue("live_u2").status)
        assertEquals(RequestStatus.PENDING, requests.requests.value.getValue("other_u2").status)
        assertEquals(RequestStatus.REJECTED, requests.requests.value.getValue("live_u3").status)
    }

    @Test
    fun `a new offer, or signed out, has no requests`() = runTest {
        requests.give(TestGuides.request.copy(guideUid = uid, userUid = "u2"))
        val vm = viewModel(null)
        collecting(vm.uiState)
        assertEquals(0, vm.state.pendingRequests)
    }

    @Test
    fun `a failed delete says so, and a new offer has nothing to delete`() = runTest {
        account.give(uid, live)
        account.failure = IllegalStateException("offline")
        val vm = viewModel("live")
        collecting(vm.uiState)
        vm.delete()
        assertEquals(SendState.FAILED, vm.state.send)
        assertFalse(vm.state.deleted)

        account.onWrite = CompletableDeferred()
        account.failure = null
        vm.setTitle("Bear") // back to editing
        vm.delete()
        assertEquals(SendState.SENDING, vm.state.send)
        vm.delete() // a second tap while deleting does nothing
        account.onWrite?.complete(Unit)
        assertTrue(vm.state.deleted)

        val new = viewModel()
        collecting(new.uiState)
        new.delete()
        assertFalse(new.state.deleted)
        assertEquals(SendState.EDITING, new.state.send)
    }

    @Test
    fun `sending needs the profile first, and works once it's there`() = runTest {
        account.profiles.value = emptyMap()
        val vm = viewModel()
        collecting(vm.uiState)
        vm.fill()
        vm.submit()
        assertTrue(vm.state.needsProfile)
        assertEquals(SendState.EDITING, vm.state.send)
        assertTrue(account.mails.isEmpty())

        account.profiles.value = mapOf(uid to TestGuides.profile.copy(email = "nope"))
        vm.submit()
        assertTrue("still incomplete", vm.state.needsProfile)

        account.profiles.value = mapOf(uid to TestGuides.profile)
        vm.submit()
        assertFalse(vm.state.needsProfile)
        assertEquals(SendState.SENT, vm.state.send)
    }

    @Test
    fun `errors show only after a first try, and follow the edits`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        assertTrue(vm.state.errors.isEmpty())
        vm.submit()
        assertTrue(OfferField.TITLE in vm.state.errors)
        assertTrue(account.mails.isEmpty())
        vm.setTitle("Bear hunt")
        assertTrue(OfferField.TITLE !in vm.state.errors)
        assertEquals(SendState.EDITING, vm.state.send)
    }

    @Test
    fun `changing the activity drops the species it doesn't cover`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.setActivity(GuideActivity.HUNT)
        vm.toggleSpecies(Species.MOOSE)
        vm.setActivity(GuideActivity.FISH)
        assertTrue(vm.state.form.species.isEmpty())
        vm.toggleSpecies(Species.WALLEYE)
        vm.toggleSpecies(Species.WALLEYE)
        assertTrue(vm.state.form.species.isEmpty())
    }

    @Test
    fun `use my location places the pin where the phone is, or says it couldn't`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.useMyLocation()
        assertTrue("no fix: said so", vm.state.locationFailed)
        assertEquals(null, vm.state.form.location)

        location.location = TestGuides.quebecCity
        vm.useMyLocation()
        assertEquals(TestGuides.quebecCity, vm.state.form.location)
        assertFalse(vm.state.locationFailed)
        assertEquals(2, location.reads)
    }

    @Test
    fun `the price keeps digits only, and the title and description are capped`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.setPrice("$2,400.00 CAD")
        assertEquals("240000", vm.state.form.priceFrom)
        vm.setDescription("x".repeat(OfferDraft.MAX_DESCRIPTION + 10))
        assertEquals(OfferDraft.MAX_DESCRIPTION, vm.state.form.description.length)
        vm.setTitle("x".repeat(OfferDraft.MAX_TITLE + 10))
        assertEquals(OfferDraft.MAX_TITLE, vm.state.form.title.length)
    }

    @Test
    fun `a failed send says so, and editing clears the message`() = runTest {
        account.failure = IllegalStateException("offline")
        val vm = viewModel()
        collecting(vm.uiState)
        vm.fill()
        vm.submit()
        assertEquals(SendState.FAILED, vm.state.send)
        vm.setRegion("Abitibi-Témiscamingue")
        assertEquals(SendState.EDITING, vm.state.send)
        account.failure = null
        vm.submit()
        assertEquals(SendState.SENT, vm.state.send)
    }

    @Test
    fun `signed out, or a build without a backend, can't send`() = runTest {
        account.failure = BackendNotConfiguredException()
        val vm = viewModel()
        collecting(vm.uiState)
        vm.fill()
        vm.submit()
        assertEquals(SendState.NOT_CONFIGURED, vm.state.send)

        account.failure = null
        auth.user.value = null
        vm.setRegion("Abitibi")
        vm.submit()
        assertEquals(SendState.FAILED, vm.state.send)
    }

    @Test
    fun `a second tap while sending does nothing`() = runTest {
        account.onWrite = CompletableDeferred()
        val vm = viewModel()
        collecting(vm.uiState)
        vm.fill()
        vm.submit()
        assertEquals(SendState.SENDING, vm.state.send)
        vm.submit()
        account.onWrite?.complete(Unit)
        assertEquals(1, account.mails.size)
        assertEquals(SendState.SENT, vm.state.send)
    }

    @Test
    fun `a cancelled send isn't reported as a failure`() = runTest {
        account.failure = CancellationException()
        val vm = viewModel()
        collecting(vm.uiState)
        vm.fill()
        vm.submit()
        assertEquals(SendState.SENDING, vm.state.send)
    }

    @Test
    fun `the edits and their errors survive process death, without reading the offer again`() = runTest {
        account.give(uid, live)
        val saved = SavedStateHandle()
        viewModel("live", saved).apply {
            collecting(uiState)
            setTitle("")
            setLocation(GeoPoint(1.0, 2.0))
            submit()
        }
        account.give(uid) // gone from the account: the restored edits don't read it
        val restored = viewModel("live", saved)
        assertEquals("the errors are back before the screen collects", setOf(OfferField.TITLE), restored.state.errors)
        collecting(restored.uiState)
        assertEquals(TestGuides.offer.copy(title = "", location = GeoPoint(1.0, 2.0)), restored.state.form)
        assertTrue(restored.state.live)
        assertTrue(restored.state.editing)
        assertFalse(restored.state.deleted)
    }

    @Test
    fun `nothing is sent while the offer is still loading`() = runTest {
        val vm = viewModel()
        vm.submit()
        assertTrue(account.mails.isEmpty())
        assertTrue(vm.state.errors.isEmpty())
    }

    private fun day(month: Int, day: Int) = LocalDate(2027, month, day)

    @Test
    fun `a new offer goes step by step, each step checked before the next`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        assertEquals(OfferStep.WHAT, vm.state.step)
        assertFalse("nothing before the first step: the screen closes", vm.back())
        vm.next()
        assertEquals("stays to fix", OfferStep.WHAT, vm.state.step)
        assertEquals(setOf(OfferField.TITLE, OfferField.DESCRIPTION, OfferField.ACTIVITY), vm.state.errors)
        vm.fill()
        assertTrue(vm.state.errors.isEmpty())
        listOf(OfferStep.SPECIES, OfferStep.WHERE, OfferStep.WHEN, OfferStep.REVIEW).forEach { step ->
            vm.next()
            assertEquals(step, vm.state.step)
        }
        assertTrue(vm.back())
        assertEquals(OfferStep.WHEN, vm.state.step)
        vm.next()
        vm.next()
        assertEquals("Next on the summary sends", SendState.SENT, vm.state.send)
    }

    @Test
    fun `an edit opens on the summary, change goes to a step and back`() = runTest {
        account.give(uid, live)
        val vm = viewModel("live")
        collecting(vm.uiState)
        assertEquals(OfferStep.REVIEW, vm.state.step)
        vm.change(OfferStep.WHERE)
        assertEquals(OfferStep.WHERE, vm.state.step)
        assertTrue(vm.state.returnToReview)
        vm.next()
        assertEquals(OfferStep.REVIEW, vm.state.step)
        vm.change(OfferStep.WHEN)
        assertTrue("back returns to the summary too", vm.back())
        assertEquals(OfferStep.REVIEW, vm.state.step)
        assertFalse("nothing before an edit's summary", vm.back())
    }

    @Test
    fun `sending with errors goes back to the first step to fix`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.fill(TestGuides.offer.copy(region = ""))
        vm.change(OfferStep.REVIEW)
        vm.submit()
        assertEquals(OfferStep.WHERE, vm.state.step)
        assertEquals(setOf(OfferField.REGION), vm.state.errors)
        vm.setRegion("Abitibi")
        vm.next()
        assertEquals(OfferStep.REVIEW, vm.state.step)
    }

    @Test
    fun `durations, half days and their mornings`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.setLength(TripLength.Days(3))
        vm.addDepartures(listOf(day(5, 20), day(5, 20), day(5, 1)), setOf(Slot.AFTERNOON))
        assertEquals("sorted, once each, no half on a 3-day trip", listOf(Departure(day(5, 1)), Departure(day(5, 20))), vm.state.form.schedule.departures)
        vm.setLength(TripLength.HalfDay)
        assertEquals("a half day leaves in the morning", listOf(Slot.MORNING, Slot.MORNING), vm.state.form.schedule.departures.map { it.slot })
        vm.addDepartures(listOf(day(6, 1)), setOf(Slot.AFTERNOON, Slot.MORNING))
        assertEquals(
            listOf(Departure(day(6, 1), Slot.MORNING), Departure(day(6, 1), Slot.AFTERNOON)),
            vm.state.form.schedule.departures.takeLast(2),
        )
        vm.setLength(TripLength.Days(1))
        assertEquals("whole days again", listOf(day(5, 1), day(5, 20), day(6, 1)), vm.state.form.schedule.departures.map { it.date })
        vm.removeDeparture(Departure(day(5, 20)))
        assertEquals(2, vm.state.form.schedule.departures.size)
        vm.setDays("1 0x")
        assertEquals(TripLength.Days(10), vm.state.form.schedule.length)
        vm.setDays("")
        assertEquals(setOf(OfferField.DURATION), vm.state.form.errors(OfferStep.WHEN))
        vm.setEveryYear(true)
        assertTrue(vm.state.form.schedule.everyYear)
        assertEquals(TestGuides.today, vm.state.today)
    }

    @Test
    fun `weekly dates, a season in one go`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.setLength(TripLength.Days(7))
        vm.addWeekly(day(5, 1), day(6, 26), emptySet())
        assertEquals(9, vm.state.form.schedule.departures.size)
        assertEquals(day(6, 26), vm.state.form.schedule.departures.last().date)
    }

    @Test
    fun `the step, the dates and the shown errors survive process death`() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved = saved).apply {
            collecting(uiState)
            fill()
            next(); next(); next()
            setLength(TripLength.HalfDay)
            addDepartures(listOf(day(6, 10)), setOf(Slot.AFTERNOON))
            setEveryYear(true)
            setDays("")
            next()
        }
        val restored = viewModel(saved = saved)
        assertEquals(OfferStep.WHEN, restored.state.step)
        assertEquals(setOf(OfferField.DURATION), restored.state.errors)
        assertEquals(
            listOf(Departure(day(5, 1)), Departure(day(6, 10))),
            restored.state.form.schedule.departures,
        )
        assertTrue(restored.state.form.schedule.everyYear)
        assertEquals(TripLength.Days(0), restored.state.form.schedule.length)
        restored.setLength(TripLength.HalfDay)
        val again = viewModel(saved = saved)
        assertEquals(TripLength.HalfDay, again.state.form.schedule.length)
    }
}
