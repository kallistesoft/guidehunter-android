package ca.kallistesoft.guidehunter.feature.account

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ca.kallistesoft.guidehunter.core.auth.AccountSession
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.model.AppSettings
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGoogleSignIn
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeSettingsRepository
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp")
class WelcomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val auth = FakeAuthRepository()
    private val google = FakeGoogleSignIn()
    private val settings = FakeSettingsRepository()
    private var signedIn = 0

    private fun str(id: Int) = app.getString(id)

    private fun launch() {
        val viewModel = WelcomeViewModel(AccountSession(auth, google, FakeGuideAccountRepository(), settings), settings)
        compose.setContent { GuideHunterTheme { WelcomeScreen(onSignedIn = { signedIn++ }, viewModel = viewModel) } }
    }

    @Test
    fun `signing in goes on to the mode choice`() {
        launch()
        compose.onNodeWithTag("sign-in").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, signedIn)
        assertEquals(TestGuides.user, auth.user.value)
    }

    @Test
    fun `while signing in the buttons wait`() {
        val hold = CompletableDeferred<Unit>()
        auth.onSignIn = hold
        launch()
        compose.onNodeWithTag("sign-in").performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.signing_in)).assertExists()
        compose.onNodeWithTag("sign-in").assertIsNotEnabled()
        compose.onNodeWithTag("later").assertIsNotEnabled()
        hold.complete(Unit)
        compose.waitForIdle()
        assertEquals(1, signedIn)
        assertEquals(1, google.shown)
    }

    @Test
    fun `a failed sign-in says so, and OK hides it`() {
        auth.failure = IllegalStateException("offline")
        launch()
        compose.onNodeWithTag("sign-in").performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.sign_in_failed)).assertExists()
        compose.onNodeWithTag("dismiss-error").performScrollTo().performClick()
        compose.onNodeWithTag("account-error").assertDoesNotExist()
        assertEquals(0, signedIn)
    }

    @Test
    fun `a backend not configured says sign-in isn't available`() {
        auth.failure = BackendNotConfiguredException()
        launch()
        compose.onNodeWithTag("sign-in").performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.sign_in_not_configured)).assertExists()
    }

    @Test
    fun `later goes to the map as a user`() {
        launch()
        compose.onNodeWithTag("later").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(AppSettings(onboarded = true, mode = AppMode.USER), settings.settings.value)
        assertEquals(0, signedIn)
    }
}
