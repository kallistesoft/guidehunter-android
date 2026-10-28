package ca.kallistesoft.guidehunter.feature.account

import android.content.Context
import ca.kallistesoft.guidehunter.core.auth.AccountSession
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.model.AppSettings
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.testing.FakeAdConsent
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGoogleSignIn
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeRequestRepository
import ca.kallistesoft.guidehunter.core.testing.FakeSettingsRepository
import ca.kallistesoft.guidehunter.core.testing.FakeUserAccountRepository
import ca.kallistesoft.guidehunter.core.model.RequestStatus
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

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context = mockk<Context>()
    private val uid = TestGuides.user.uid
    private val auth = FakeAuthRepository()
    private val google = FakeGoogleSignIn()
    private val requests = FakeRequestRepository()
    private val account = FakeGuideAccountRepository(requests)
    private val users = FakeUserAccountRepository()
    private val settings = FakeSettingsRepository(AppSettings(onboarded = true))
    private val consent = FakeAdConsent()

    private fun viewModel() =
        SettingsViewModel(AccountSession(auth, google, account, users, requests, settings), settings, consent, users, requests)

    private val SettingsViewModel.state get() = uiState.value

    @Test
    fun `loads the account, the mode and the privacy options`() = runTest {
        auth.user.value = TestGuides.user
        settings.settings.value = AppSettings(onboarded = true, mode = AppMode.GUIDE)
        consent.privacyOptionsRequired.value = true
        val vm = viewModel()
        assertTrue(vm.state.loading)
        collecting(vm.uiState)
        assertEquals(SettingsUiState(false, TestGuides.user, AppMode.GUIDE, privacyOptionsRequired = true), vm.state)
        settings.setSpeciesFilter(setOf(Species.MOOSE))
        assertEquals("the species the map shows", setOf(Species.MOOSE), vm.state.speciesFilter)
    }

    @Test
    fun `the contact and the pending requests, sent and received`() = runTest {
        auth.user.value = TestGuides.user
        users.contacts.value = mapOf(uid to TestGuides.userContact)
        requests.give(
            TestGuides.request,
            TestGuides.request.copy(id = "salmon_$uid", offerId = "salmon", status = RequestStatus.APPROVED),
            TestGuides.request.copy(id = "o1_u2", offerId = "o1", guideUid = uid, userUid = "u2"),
        )
        val vm = viewModel()
        collecting(vm.uiState)
        assertEquals(TestGuides.userContact, vm.state.contact)
        assertEquals(1, vm.state.pendingSent)
        assertEquals(1, vm.state.pendingReceived)
        auth.user.value = null
        assertNull(vm.state.contact)
        assertEquals(0, vm.state.pendingSent)
    }

    @Test
    fun `switching to guide mode signed out signs in first`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.switchMode(context)
        assertEquals(1, google.shown)
        assertEquals(TestGuides.user, vm.state.user)
        assertEquals(AppMode.GUIDE, vm.state.mode)
    }

    @Test
    fun `closing Google's sheet stays in user mode`() = runTest {
        google.token = null
        val vm = viewModel()
        collecting(vm.uiState)
        vm.switchMode(context)
        assertEquals(AppMode.USER, vm.state.mode)
        assertNull(vm.state.error)
    }

    @Test
    fun `signed in, switching goes to guide mode and back without the sheet`() = runTest {
        auth.user.value = TestGuides.user
        val vm = viewModel()
        collecting(vm.uiState)
        vm.switchMode(context)
        assertEquals(AppMode.GUIDE, vm.state.mode)
        vm.switchMode(context)
        assertEquals(AppMode.USER, vm.state.mode)
        assertEquals(0, google.shown)
    }

    @Test
    fun `a failed sign-in while switching says so and stays in user mode`() = runTest {
        auth.failure = IllegalStateException("offline")
        val vm = viewModel()
        collecting(vm.uiState)
        vm.switchMode(context)
        assertEquals(AccountError.SIGN_IN_FAILED, vm.state.error)
        assertEquals(AppMode.USER, vm.state.mode)
        vm.dismissError()
        assertNull(vm.state.error)
    }

    @Test
    fun `signing in shows the account, busy meanwhile`() = runTest {
        val hold = CompletableDeferred<Unit>()
        auth.onSignIn = hold
        val vm = viewModel()
        collecting(vm.uiState)
        vm.signIn(context)
        assertTrue(vm.state.busy)
        vm.signIn(context)
        assertEquals(1, google.shown)
        hold.complete(Unit)
        assertFalse(vm.state.busy)
        assertEquals(TestGuides.user, vm.state.user)
    }

    @Test
    fun `a backend not configured says sign-in isn't available`() = runTest {
        auth.failure = BackendNotConfiguredException()
        val vm = viewModel()
        collecting(vm.uiState)
        vm.signIn(context)
        assertEquals(AccountError.NOT_CONFIGURED, vm.state.error)
    }

    @Test
    fun `signing out goes back to user mode`() = runTest {
        auth.user.value = TestGuides.user
        settings.settings.value = AppSettings(onboarded = true, mode = AppMode.GUIDE)
        val vm = viewModel()
        collecting(vm.uiState)
        vm.signOut(context)
        assertNull(vm.state.user)
        assertEquals(AppMode.USER, vm.state.mode)
        assertEquals(1, google.signedOut)
    }

    @Test
    fun `deleting the account deletes everything and starts over`() = runTest {
        auth.user.value = TestGuides.user
        account.profiles.value = mapOf(uid to TestGuides.profile)
        settings.settings.value = AppSettings(onboarded = true, mode = AppMode.GUIDE)
        val vm = viewModel()
        collecting(vm.uiState)
        vm.deleteAccount(context)
        assertTrue(auth.deleted)
        assertTrue(account.profiles.value.isEmpty())
        assertEquals(AppSettings(), settings.settings.value)
        assertNull(vm.state.error)
    }

    @Test
    fun `a failed deletion says so and keeps the account`() = runTest {
        auth.user.value = TestGuides.user
        auth.failure = IllegalStateException("offline")
        val vm = viewModel()
        collecting(vm.uiState)
        vm.deleteAccount(context)
        assertEquals(AccountError.DELETE_FAILED, vm.state.error)
        assertEquals(TestGuides.user, vm.state.user)
        assertFalse(auth.deleted)
    }

    @Test
    fun `privacy options open the consent choices`() = runTest {
        val vm = viewModel()
        vm.showPrivacyOptions()
        assertEquals(1, consent.privacyOptionsShown)
    }
}
