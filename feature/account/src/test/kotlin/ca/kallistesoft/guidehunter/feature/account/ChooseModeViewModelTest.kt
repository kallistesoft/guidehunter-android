package ca.kallistesoft.guidehunter.feature.account

import ca.kallistesoft.guidehunter.core.auth.AccountSession
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.model.AppSettings
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGoogleSignIn
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeSettingsRepository
import ca.kallistesoft.guidehunter.core.testing.MainDispatcherRule
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.testing.collecting
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChooseModeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository(TestGuides.user)
    private val settings = FakeSettingsRepository()

    private fun viewModel() =
        ChooseModeViewModel(AccountSession(auth, FakeGoogleSignIn(), FakeGuideAccountRepository(), settings), settings)

    @Test
    fun `shows the signed-in email, empty when signed out`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        assertEquals(ChooseModeUiState("guide@example.com"), vm.uiState.value)
        auth.user.value = null
        assertEquals(ChooseModeUiState(""), vm.uiState.value)
    }

    @Test
    fun `choosing a mode finishes the welcome on it`() = runTest {
        val vm = viewModel()
        vm.choose(AppMode.GUIDE)
        assertEquals(AppSettings(onboarded = true, mode = AppMode.GUIDE), settings.settings.value)
        vm.choose(AppMode.USER)
        assertEquals(AppSettings(onboarded = true, mode = AppMode.USER), settings.settings.value)
    }
}
