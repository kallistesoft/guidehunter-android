package ca.kallistesoft.guidehunter.feature.account

import android.app.Application
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ca.kallistesoft.guidehunter.core.auth.AccountSession
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.model.AppSettings
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.testing.FakeAdConsent
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGoogleSignIn
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeSettingsRepository
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp")
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val auth = FakeAuthRepository()
    private val google = FakeGoogleSignIn()
    private val settings = FakeSettingsRepository(AppSettings(onboarded = true))
    private val consent = FakeAdConsent()
    private var backs = 0
    private var profiles = 0
    private var speciesFilters = 0

    private fun str(id: Int) = app.getString(id)

    private fun launch() {
        val session = AccountSession(auth, google, FakeGuideAccountRepository(), settings)
        val viewModel = SettingsViewModel(session, settings, consent)
        compose.setContent {
            GuideHunterTheme {
                SettingsScreen(onBack = { backs++ }, onGuideProfile = { profiles++ }, onSpeciesFilter = { speciesFilters++ }, viewModel = viewModel)
            }
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** Outside the scrolling column: the top bar, the dialog. */
    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    private fun signedInGuide() {
        auth.user.value = TestGuides.user
        settings.settings.value = AppSettings(onboarded = true, mode = AppMode.GUIDE)
    }

    @Test
    fun `user mode signed out offers guide mode and sign-in`() {
        launch()
        compose.onNodeWithText(str(R.string.switch_to_guide)).assertExists()
        compose.onNodeWithTag("guide-profile").assertDoesNotExist()
        compose.onNodeWithTag("account").assertDoesNotExist()
        compose.onNodeWithTag("sign-out").assertDoesNotExist()
        compose.onNodeWithTag("delete-account").assertDoesNotExist()
        compose.onNodeWithTag("privacy-options").assertDoesNotExist()
        click("sign-in")
        assertEquals(TestGuides.user, auth.user.value)
        compose.onNodeWithTag("account").assertExists()
        compose.onNodeWithText("guide@example.com").assertExists()
        compose.onNodeWithTag("sign-in").assertDoesNotExist()
    }

    @Test
    fun `user mode shows the species on the map and opens their screen`() {
        launch()
        compose.onNodeWithTag("species-filter").assertTextContains(str(R.string.species_filter_any), substring = true)
        settings.settings.value = AppSettings(onboarded = true, speciesFilter = setOf(Species.WALLEYE, Species.MOOSE))
        compose.waitForIdle()
        // In the pickers' order: hunting first.
        val names = "${str(ca.kallistesoft.guidehunter.core.ui.R.string.species_moose)}, ${str(ca.kallistesoft.guidehunter.core.ui.R.string.species_walleye)}"
        compose.onNodeWithTag("species-filter").assertTextContains(names, substring = true)
        click("species-filter")
        assertEquals(1, speciesFilters)
    }

    @Test
    fun `guide mode has no species row`() {
        signedInGuide()
        launch()
        compose.onNodeWithTag("guide-profile").assertExists()
        compose.onNodeWithTag("species-filter").assertDoesNotExist()
    }

    @Test
    fun `switching to guide mode signed out signs in first`() {
        launch()
        click("switch-mode")
        assertEquals(1, google.shown)
        assertEquals(AppMode.GUIDE, settings.settings.value.mode)
        compose.onNodeWithTag("guide-profile").assertExists()
        compose.onNodeWithText(str(R.string.switch_to_user)).assertExists()
    }

    @Test
    fun `closing Google's sheet stays in user mode`() {
        google.token = null
        launch()
        click("switch-mode")
        assertEquals(AppMode.USER, settings.settings.value.mode)
        compose.onNodeWithTag("account-error").assertDoesNotExist()
    }

    @Test
    fun `guide mode opens the profile, goes back and switches to user mode`() {
        signedInGuide()
        launch()
        click("guide-profile")
        assertEquals(1, profiles)
        tap("back")
        assertEquals(1, backs)
        click("switch-mode")
        assertEquals(AppMode.USER, settings.settings.value.mode)
        compose.onNodeWithTag("guide-profile").assertDoesNotExist()
    }

    @Test
    fun `signing out goes back to user mode`() {
        signedInGuide()
        launch()
        click("sign-out")
        assertNull(auth.user.value)
        assertEquals(AppMode.USER, settings.settings.value.mode)
        compose.onNodeWithTag("sign-in").assertExists()
    }

    @Test
    fun `deleting the account asks first, then starts over`() {
        signedInGuide()
        launch()
        click("delete-account")
        tap("delete-account-cancel")
        compose.onNodeWithTag("delete-account-confirm").assertDoesNotExist()
        assertFalse(auth.deleted)
        click("delete-account")
        compose.onNodeWithText(str(R.string.delete_account_title)).assertExists()
        tap("delete-account-confirm")
        assertTrue(auth.deleted)
        assertEquals(AppSettings(), settings.settings.value)
    }

    @Test
    fun `a failed deletion says so, and OK hides it`() {
        signedInGuide()
        auth.failure = IllegalStateException("offline")
        launch()
        click("delete-account")
        tap("delete-account-confirm")
        compose.onNodeWithText(str(R.string.delete_account_failed)).assertExists()
        assertFalse(auth.deleted)
        click("dismiss-error")
        compose.onNodeWithTag("account-error").assertDoesNotExist()
    }

    @Test
    fun `privacy options show only where required, and open the consent choices`() {
        consent.privacyOptionsRequired.value = true
        launch()
        click("privacy-options")
        assertEquals(1, consent.privacyOptionsShown)
    }

    @Test
    fun `loading shows a spinner, not the settings`() {
        val actions = SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, {})
        compose.setContent { GuideHunterTheme { SettingsContent(SettingsUiState(), actions) } }
        compose.onNodeWithTag("settings-screen").assertDoesNotExist()
    }
}
