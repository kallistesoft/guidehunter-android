package ca.kallistesoft.guidehunter.feature.account

import android.content.Context
import ca.kallistesoft.guidehunter.core.auth.AccountSession
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.model.AppSettings
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGoogleSignIn
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeSettingsRepository
import ca.kallistesoft.guidehunter.core.testing.MainDispatcherRule
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.testing.collecting
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WelcomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context = mockk<Context>()
    private val auth = FakeAuthRepository()
    private val google = FakeGoogleSignIn()
    private val settings = FakeSettingsRepository()
    private val session = AccountSession(auth, google, FakeGuideAccountRepository(), settings)
    private var signedIn = 0

    private fun viewModel() = WelcomeViewModel(session, settings)

    @Test
    fun `signing in calls back once signed in`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.signIn(context) { signedIn++ }
        assertEquals(1, signedIn)
        assertEquals(TestGuides.user, auth.user.value)
        assertEquals(WelcomeUiState(), vm.uiState.value)
    }

    @Test
    fun `closing Google's sheet is no error and doesn't go on`() = runTest {
        google.token = null
        val vm = viewModel()
        collecting(vm.uiState)
        vm.signIn(context) { signedIn++ }
        assertEquals(0, signedIn)
        assertNull(auth.user.value)
        assertEquals(WelcomeUiState(), vm.uiState.value)
    }

    @Test
    fun `a failed sign-in says so until dismissed`() = runTest {
        auth.failure = IllegalStateException("offline")
        val vm = viewModel()
        collecting(vm.uiState)
        vm.signIn(context) { signedIn++ }
        assertEquals(WelcomeUiState(error = AccountError.SIGN_IN_FAILED), vm.uiState.value)
        assertEquals(0, signedIn)
        vm.dismissError()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `a backend not configured says sign-in isn't available`() = runTest {
        auth.failure = BackendNotConfiguredException()
        val vm = viewModel()
        collecting(vm.uiState)
        vm.signIn(context) { signedIn++ }
        assertEquals(AccountError.NOT_CONFIGURED, vm.uiState.value.error)
    }

    @Test
    fun `a second tap while signing in is ignored`() = runTest {
        val hold = CompletableDeferred<Unit>()
        auth.onSignIn = hold
        val vm = viewModel()
        collecting(vm.uiState)
        vm.signIn(context) { signedIn++ }
        assertTrue(vm.uiState.value.busy)
        vm.signIn(context) { signedIn++ }
        assertEquals(1, google.shown)
        hold.complete(Unit)
        assertEquals(1, signedIn)
        assertEquals(WelcomeUiState(), vm.uiState.value)
    }

    @Test
    fun `later finishes the welcome as a user`() = runTest {
        val vm = viewModel()
        vm.later()
        assertEquals(AppSettings(onboarded = true, mode = AppMode.USER), settings.settings.value)
    }
}
