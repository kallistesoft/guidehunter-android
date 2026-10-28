package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.lifecycle.SavedStateHandle
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.ProfileField
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.MainDispatcherRule
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.testing.collecting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GuideProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val uid = TestGuides.user.uid
    private val auth = FakeAuthRepository(TestGuides.user)
    private val account = FakeGuideAccountRepository()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = GuideProfileViewModel(auth, account, saved)

    private val GuideProfileViewModel.state get() = uiState.value

    @Test
    fun `a first profile starts with the Google email, and is saved`() = runTest {
        val vm = viewModel()
        assertTrue(vm.state.loading)
        collecting(vm.uiState)
        assertEquals(GuideProfile(email = "guide@example.com"), vm.state.form)
        vm.setBusinessName(" Pourvoirie Test ")
        vm.setPhone("819 555 0100")
        vm.setWebsite("example.com")
        vm.submit()
        assertEquals(SendState.SENT, vm.state.send)
        assertEquals(TestGuides.profile.copy(website = "example.com"), account.profiles.value[uid])
        vm.submit()
        assertEquals(SendState.SENT, vm.state.send)
    }

    @Test
    fun `a saved profile opens as it was`() = runTest {
        account.profiles.value = mapOf(uid to TestGuides.profile)
        val vm = viewModel()
        collecting(vm.uiState)
        assertEquals(TestGuides.profile, vm.state.form)
        vm.setEmail("other@example.com")
        vm.submit()
        assertEquals("other@example.com", account.profiles.value.getValue(uid).email)
    }

    @Test
    fun `errors show after a first try, and follow the edits`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.setEmail("")
        assertTrue(vm.state.errors.isEmpty())
        vm.submit()
        assertEquals(setOf(ProfileField.BUSINESS_NAME, ProfileField.EMAIL), vm.state.errors)
        vm.setBusinessName("Camp")
        assertEquals(setOf(ProfileField.EMAIL), vm.state.errors)
    }

    @Test
    fun `a failed save says so, and editing clears the message`() = runTest {
        account.profiles.value = mapOf(uid to TestGuides.profile)
        account.failure = IllegalStateException("offline")
        val vm = viewModel()
        collecting(vm.uiState)
        vm.submit()
        assertEquals(SendState.FAILED, vm.state.send)
        vm.setPhone("")
        assertEquals(SendState.EDITING, vm.state.send)

        account.failure = BackendNotConfiguredException()
        vm.submit()
        assertEquals(SendState.NOT_CONFIGURED, vm.state.send)

        account.failure = CancellationException()
        vm.setPhone("819 555 0100")
        vm.submit()
        assertEquals("cancelled: not a failure", SendState.SENDING, vm.state.send)
    }

    @Test
    fun `a second tap while saving does nothing, nor one before loading`() = runTest {
        account.profiles.value = mapOf(uid to TestGuides.profile)
        account.onWrite = CompletableDeferred()
        val vm = viewModel()
        vm.submit()
        collecting(vm.uiState)
        vm.submit()
        vm.submit()
        assertEquals(SendState.SENDING, vm.state.send)
        account.onWrite?.complete(Unit)
        assertEquals(SendState.SENT, vm.state.send)
    }

    @Test
    fun `signed out, nothing can be saved`() = runTest {
        auth.user.value = null
        val vm = viewModel()
        collecting(vm.uiState)
        assertEquals(GuideProfile(), vm.state.form)
        vm.setBusinessName("Camp")
        vm.setEmail("a@b.ca")
        vm.submit()
        assertEquals(SendState.FAILED, vm.state.send)
    }

    @Test
    fun `the edits and their errors survive process death`() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).apply {
            collecting(uiState)
            setBusinessName("Camp")
            setEmail("bad")
            submit()
        }
        account.profiles.value = mapOf(uid to TestGuides.profile) // not read again: the edits win
        val restored = viewModel(saved)
        assertEquals(setOf(ProfileField.EMAIL), restored.state.errors)
        collecting(restored.uiState)
        assertEquals(GuideProfile(businessName = "Camp", email = "bad"), restored.state.form)
    }
}
