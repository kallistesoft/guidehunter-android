package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.lifecycle.SavedStateHandle
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import ca.kallistesoft.guidehunter.core.model.OfferField
import ca.kallistesoft.guidehunter.core.model.OfferStatus
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeLocationRepository
import ca.kallistesoft.guidehunter.core.testing.MainDispatcherRule
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.testing.collecting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
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
    private val account = FakeGuideAccountRepository().apply { profiles.value = mapOf(uid to TestGuides.profile) }
    private val location = FakeLocationRepository()
    private val live = GuideOffer("live", TestGuides.offer, OfferStatus.PUBLISHED, live = true)

    private fun viewModel(offerId: String? = null, saved: SavedStateHandle = SavedStateHandle()) =
        OfferEditorViewModel(offerId, auth, account, location, saved)

    /** Types [offer] into the form, field by field, as the guide would. */
    private fun OfferEditorViewModel.fill(offer: OfferDraft = TestGuides.offer) {
        setTitle(offer.title)
        setDescription(offer.description)
        offer.activity?.let(::setActivity)
        offer.species.forEach(::toggleSpecies)
        setRegion(offer.region)
        offer.location?.let(::setLocation)
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
    fun `an offer is deleted, and taken off the map`() = runTest {
        account.give(uid, live)
        val vm = viewModel("live")
        collecting(vm.uiState)
        vm.delete()
        assertTrue(vm.state.deleted)
        assertEquals(emptyList<GuideOffer>(), account.offersByUser.value[uid])
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
        vm.setActivity(GuideActivity.BOTH)
        vm.toggleSpecies(Species.MOOSE)
        vm.toggleSpecies(Species.WALLEYE)
        vm.setActivity(GuideActivity.FISH)
        assertEquals(setOf(Species.WALLEYE), vm.state.form.species)
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
}
