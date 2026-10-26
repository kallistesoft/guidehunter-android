package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.lifecycle.SavedStateHandle
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.model.ApplicationField
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.GuideApplication
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.testing.FakeGuideApplicationRepository
import ca.kallistesoft.guidehunter.core.testing.MainDispatcherRule
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.testing.collecting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BecomeGuideViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeGuideApplicationRepository()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = BecomeGuideViewModel(repository, saved)

    /** Types [application] into the form, field by field, as the guide would. */
    private fun BecomeGuideViewModel.fill(application: GuideApplication = TestGuides.application) {
        setBusinessName(application.businessName)
        setDescription(application.description)
        application.activity?.let(::setActivity)
        application.species.forEach(::toggleSpecies)
        setRegion(application.region)
        application.location?.let(::setLocation)
        setPrice(application.priceFrom)
        setEmail(application.email)
        setPhone(application.phone)
        setWebsite(application.website)
    }

    private val BecomeGuideViewModel.state get() = uiState.value

    @Test
    fun `a complete form is sent as typed`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.fill()
        assertEquals(TestGuides.application, vm.state.form)
        vm.submit()
        assertEquals(SendState.SENT, vm.state.send)
        assertEquals(listOf(TestGuides.application), repository.submitted)
        vm.submit()
        assertEquals("sent once", 1, repository.submitted.size)
    }

    @Test
    fun `errors show only after a first try, and follow the edits`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        assertTrue(vm.state.errors.isEmpty())
        vm.submit()
        assertTrue(ApplicationField.BUSINESS_NAME in vm.state.errors)
        assertTrue(repository.submitted.isEmpty())
        vm.setBusinessName("Camp")
        assertTrue(ApplicationField.BUSINESS_NAME !in vm.state.errors)
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
    fun `the price keeps digits only, and the description is capped`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.setPrice("$2,400.00 CAD")
        assertEquals("240000", vm.state.form.priceFrom)
        vm.setDescription("x".repeat(GuideApplication.MAX_DESCRIPTION + 10))
        assertEquals(GuideApplication.MAX_DESCRIPTION, vm.state.form.description.length)
    }

    @Test
    fun `a failed send says so, and editing clears the message`() = runTest {
        repository.failure = IllegalStateException("offline")
        val vm = viewModel()
        collecting(vm.uiState)
        vm.fill()
        vm.submit()
        assertEquals(SendState.FAILED, vm.state.send)
        vm.setPhone("819 555 0199")
        assertEquals(SendState.EDITING, vm.state.send)
        repository.failure = null
        vm.submit()
        assertEquals(SendState.SENT, vm.state.send)
    }

    @Test
    fun `a build without a backend says the form can't be sent`() = runTest {
        repository.failure = BackendNotConfiguredException()
        val vm = viewModel()
        collecting(vm.uiState)
        vm.fill()
        vm.submit()
        assertEquals(SendState.NOT_CONFIGURED, vm.state.send)
        vm.setRegion("Abitibi-Témiscamingue")
        assertEquals(SendState.EDITING, vm.state.send)
    }

    @Test
    fun `a second tap while sending does nothing`() = runTest {
        repository.onSubmit = CompletableDeferred()
        val vm = viewModel()
        collecting(vm.uiState)
        vm.fill()
        vm.submit()
        assertEquals(SendState.SENDING, vm.state.send)
        vm.submit()
        repository.onSubmit?.complete(Unit)
        assertEquals(1, repository.submitted.size)
    }

    @Test
    fun `a cancelled send isn't reported as a failure`() = runTest {
        repository.failure = CancellationException()
        val vm = viewModel()
        collecting(vm.uiState)
        vm.fill()
        vm.submit()
        assertEquals(SendState.SENDING, vm.state.send)
    }

    @Test
    fun `the form and its errors survive process death`() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).apply {
            fill(TestGuides.application.copy(website = "example.com", location = GeoPoint(1.0, 2.0)))
            setEmail("")
            submit()
        }
        val restored = viewModel(saved)
        collecting(restored.uiState)
        assertEquals(TestGuides.application.copy(website = "example.com", location = GeoPoint(1.0, 2.0), email = ""), restored.state.form)
        assertEquals(setOf(ApplicationField.EMAIL), restored.state.errors)

        val fresh = viewModel(SavedStateHandle())
        collecting(fresh.uiState)
        assertEquals(GuideApplication(), fresh.state.form)
    }
}
