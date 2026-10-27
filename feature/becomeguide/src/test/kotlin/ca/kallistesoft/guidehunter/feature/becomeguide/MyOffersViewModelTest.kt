package ca.kallistesoft.guidehunter.feature.becomeguide

import android.content.Context
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.MainDispatcherRule
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.testing.collecting
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MyOffersViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val uid = TestGuides.user.uid
    private val auth = FakeAuthRepository()
    private val account = FakeGuideAccountRepository()
    private val google = FakeGoogleSignIn()
    private val activity = mockk<Context>()
    private val offer = GuideOffer("o1", TestGuides.offer)

    private fun viewModel() = MyOffersViewModel(auth, account, google)

    private val MyOffersViewModel.state get() = uiState.value

    @Test
    fun `signed out, then signed in with Google, the guide's profile and offers, live`() = runTest {
        val vm = viewModel()
        assertTrue(vm.state.loading)
        collecting(vm.uiState)
        assertFalse(vm.state.loading)
        assertNull(vm.state.user)

        vm.signIn(activity)
        assertEquals(TestGuides.user, vm.state.user)
        assertNull(vm.state.profile)
        assertTrue(vm.state.offers.isEmpty())

        account.profiles.value = mapOf(uid to TestGuides.profile)
        account.give(uid, offer)
        assertEquals(TestGuides.profile, vm.state.profile)
        assertEquals(listOf(offer), vm.state.offers)
    }

    @Test
    fun `closing Google's sheet is no error`() = runTest {
        google.token = null
        val vm = viewModel()
        collecting(vm.uiState)
        vm.signIn(activity)
        assertNull(vm.state.user)
        assertNull(vm.state.error)
        assertFalse(vm.state.busy)
    }

    @Test
    fun `a failed sign-in says so until dismissed, and a build without Firebase says why`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        google.failure = IllegalStateException("no network")
        vm.signIn(activity)
        assertEquals(AccountError.SIGN_IN_FAILED, vm.state.error)
        vm.dismissError()
        assertNull(vm.state.error)

        google.failure = BackendNotConfiguredException()
        vm.signIn(activity)
        assertEquals(AccountError.NOT_CONFIGURED, vm.state.error)
    }

    @Test
    fun `a second tap while signing in does nothing`() = runTest {
        auth.onSignIn = CompletableDeferred()
        val vm = viewModel()
        collecting(vm.uiState)
        vm.signIn(activity)
        assertTrue(vm.state.busy)
        vm.signIn(activity)
        assertEquals(1, google.shown)
        auth.onSignIn?.complete(Unit)
        assertFalse(vm.state.busy)
        assertEquals(TestGuides.user, vm.state.user)
    }

    @Test
    fun `sign out forgets the Google account`() = runTest {
        auth.user.value = TestGuides.user
        val vm = viewModel()
        collecting(vm.uiState)
        vm.signOut(activity)
        assertNull(vm.state.user)
        assertEquals(1, google.signedOut)
    }

    @Test
    fun `deleting the account confirms it with Google, then deletes everything`() = runTest {
        auth.user.value = TestGuides.user
        account.profiles.value = mapOf(uid to TestGuides.profile)
        account.give(uid, offer)
        val vm = viewModel()
        collecting(vm.uiState)
        vm.deleteAccount(activity)
        assertEquals(listOf(FakeAuthRepository.TOKEN), auth.reauthenticated)
        assertTrue(auth.deleted)
        assertTrue(account.offersByUser.value.isEmpty())
        assertTrue(account.profiles.value.isEmpty())
        assertNull(vm.state.user)
        assertEquals(1, google.signedOut)
        assertNull(vm.state.error)
    }

    @Test
    fun `closing Google's sheet, or being signed out, deletes nothing`() = runTest {
        auth.user.value = TestGuides.user
        account.give(uid, offer)
        google.token = null
        val vm = viewModel()
        collecting(vm.uiState)
        vm.deleteAccount(activity)
        assertFalse(auth.deleted)
        assertEquals(listOf(offer), vm.state.offers)

        auth.user.value = null
        google.token = FakeAuthRepository.TOKEN
        vm.deleteAccount(activity)
        assertFalse(auth.deleted)
        assertEquals(1, google.shown)
    }

    @Test
    fun `a failed deletion says so and keeps the account`() = runTest {
        auth.user.value = TestGuides.user
        account.give(uid, offer)
        account.failure = IllegalStateException("offline")
        val vm = viewModel()
        collecting(vm.uiState)
        vm.deleteAccount(activity)
        assertEquals(AccountError.DELETE_FAILED, vm.state.error)
        assertFalse(auth.deleted)
        assertEquals(TestGuides.user, vm.state.user)
    }
}
