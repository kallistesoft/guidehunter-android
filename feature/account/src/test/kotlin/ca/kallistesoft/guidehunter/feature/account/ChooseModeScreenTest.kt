package ca.kallistesoft.guidehunter.feature.account

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ca.kallistesoft.guidehunter.core.auth.AccountSession
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.model.AppSettings
import ca.kallistesoft.guidehunter.core.model.GuideUser
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGoogleSignIn
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeRequestRepository
import ca.kallistesoft.guidehunter.core.testing.FakeUserAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeSettingsRepository
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp")
class ChooseModeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val settings = FakeSettingsRepository()

    private fun launch(user: GuideUser? = TestGuides.user) {
        val session = AccountSession(FakeAuthRepository(user), FakeGoogleSignIn(), FakeGuideAccountRepository(), FakeUserAccountRepository(), FakeRequestRepository(), settings)
        val viewModel = ChooseModeViewModel(session, settings)
        compose.setContent { GuideHunterTheme { ChooseModeScreen(viewModel = viewModel) } }
    }

    @Test
    fun `shows who is signed in, and choosing user opens the map`() {
        launch()
        compose.onNodeWithTag("signed-in-as").assertExists()
        compose.onNodeWithText(app.getString(R.string.signed_in_as, "guide@example.com")).assertExists()
        compose.onNodeWithTag("mode-user").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(AppSettings(onboarded = true, mode = AppMode.USER), settings.settings.value)
    }

    @Test
    fun `choosing guide opens the guide's home`() {
        launch()
        compose.onNodeWithTag("mode-guide").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(AppSettings(onboarded = true, mode = AppMode.GUIDE), settings.settings.value)
    }

    @Test
    fun `no email yet, no signed-in line`() {
        launch(user = null)
        compose.onNodeWithTag("mode-user").assertExists()
        compose.onNodeWithTag("signed-in-as").assertDoesNotExist()
    }
}
