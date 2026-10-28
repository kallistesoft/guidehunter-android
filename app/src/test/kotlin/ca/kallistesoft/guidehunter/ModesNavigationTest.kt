package ca.kallistesoft.guidehunter

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ca.kallistesoft.guidehunter.core.auth.GoogleSignIn
import ca.kallistesoft.guidehunter.core.auth.GoogleSignInModule
import ca.kallistesoft.guidehunter.core.data.AuthRepository
import ca.kallistesoft.guidehunter.core.data.GuideAccountRepository
import ca.kallistesoft.guidehunter.core.data.GuideRepository
import ca.kallistesoft.guidehunter.core.data.LocationRepository
import ca.kallistesoft.guidehunter.core.data.SettingsRepository
import ca.kallistesoft.guidehunter.core.data.di.DataModule
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.model.AppSettings
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGoogleSignIn
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGuideRepository
import ca.kallistesoft.guidehunter.core.testing.FakeLocationRepository
import ca.kallistesoft.guidehunter.core.testing.FakeSettingsRepository
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app's homes with fake settings, account and Google sheet: the welcome on a first launch (sign in then choose a
 * mode, or "Later"), switching modes, signing out and deleting the account from the settings, and a relaunch.
 * Each test starts on a first launch, signed out; a test that needs another start sets the fakes, and the home follows.
 */
@HiltAndroidTest
@UninstallModules(DataModule::class, GoogleSignInModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "en-w411dp-h891dp")
class ModesNavigationTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val fakeAuth = FakeAuthRepository()
    private val fakeSettings = FakeSettingsRepository()
    private val fakeGoogle = FakeGoogleSignIn()

    @BindValue @JvmField
    val auth: AuthRepository = fakeAuth

    @BindValue @JvmField
    val settings: SettingsRepository = fakeSettings

    @BindValue @JvmField
    val google: GoogleSignIn = fakeGoogle

    @BindValue @JvmField
    val account: GuideAccountRepository = FakeGuideAccountRepository()

    @BindValue @JvmField
    val guides: GuideRepository = FakeGuideRepository()

    @BindValue @JvmField
    val location: LocationRepository = FakeLocationRepository()

    @Before
    fun setUp() = hilt.inject()

    private fun tag(tag: String) = compose.onNodeWithTag(tag)

    private fun waitFor(tag: String) =
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForNo(tag: String) =
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }

    /** Starts onboarded in [mode], signed in or not; the home follows. */
    private fun start(mode: AppMode, signedIn: Boolean) {
        fakeAuth.user.value = if (signedIn) TestGuides.user else null
        fakeSettings.settings.value = AppSettings(onboarded = true, mode = mode)
    }

    /** Back on the home (the only entry) leaves the app, as Android does. */
    private fun assertBackLeavesTheApp() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.activityRule.scenario.onActivity { assertTrue(it.isFinishing) }
    }

    @Test
    fun `a first launch signs in, chooses guide, and opens on the offers alone`() {
        waitFor("welcome")
        tag("sign-in").performClick()
        waitFor("mode-guide")
        assertEquals(TestGuides.user, fakeAuth.user.value)
        tag("mode-guide").performClick()
        waitFor("profile")
        assertEquals(AppSettings(onboarded = true, mode = AppMode.GUIDE), fakeSettings.settings.value)
        tag("welcome").assertDoesNotExist()
        assertBackLeavesTheApp()
    }

    @Test
    fun `a first launch signs in, chooses user, and opens on the map`() {
        waitFor("welcome")
        tag("sign-in").performClick()
        waitFor("mode-user")
        tag("mode-user").performClick()
        waitFor("locate")
        assertEquals(AppSettings(onboarded = true, mode = AppMode.USER), fakeSettings.settings.value)
        assertBackLeavesTheApp()
    }

    @Test
    fun `later on the welcome opens the map as a user, signed out`() {
        waitFor("welcome")
        tag("later").performClick()
        waitFor("locate")
        assertEquals(AppSettings(onboarded = true, mode = AppMode.USER), fakeSettings.settings.value)
        assertNull(fakeAuth.user.value)
        assertBackLeavesTheApp()
    }

    @Test
    fun `switching to guide in the settings, signed in, lands on the offers alone`() {
        start(AppMode.USER, signedIn = true)
        waitFor("locate")
        tag("settings").performClick()
        waitFor("switch-mode")
        tag("switch-mode").performClick()
        waitFor("profile")
        assertEquals(AppMode.GUIDE, fakeSettings.settings.value.mode)
        assertEquals(0, fakeGoogle.shown) // already signed in: no sheet
        tag("settings-screen").assertDoesNotExist()
        assertBackLeavesTheApp()
    }

    @Test
    fun `switching to guide in the settings, signed out, signs in first`() {
        start(AppMode.USER, signedIn = false)
        waitFor("locate")
        tag("settings").performClick()
        waitFor("switch-mode")
        tag("switch-mode").performClick()
        waitFor("profile")
        assertEquals(1, fakeGoogle.shown)
        assertEquals(TestGuides.user, fakeAuth.user.value)
        assertBackLeavesTheApp()
    }

    @Test
    fun `switching to user in the settings lands on the map alone`() {
        start(AppMode.GUIDE, signedIn = true)
        waitFor("profile")
        tag("settings").performClick()
        waitFor("switch-mode")
        tag("switch-mode").performClick()
        waitFor("locate")
        assertEquals(AppMode.USER, fakeSettings.settings.value.mode)
        tag("settings-screen").assertDoesNotExist()
        assertBackLeavesTheApp()
    }

    @Test
    fun `signing out in guide mode lands on the map as a user`() {
        start(AppMode.GUIDE, signedIn = true)
        waitFor("profile")
        tag("settings").performClick()
        waitFor("sign-out")
        tag("sign-out").performScrollTo().performClick()
        waitFor("locate")
        assertNull(fakeAuth.user.value)
        assertEquals(AppSettings(onboarded = true, mode = AppMode.USER), fakeSettings.settings.value)
        assertEquals(1, fakeGoogle.signedOut)
        assertBackLeavesTheApp()
    }

    @Test
    fun `deleting the account, once confirmed, starts over at the welcome`() {
        start(AppMode.GUIDE, signedIn = true)
        waitFor("profile")
        tag("settings").performClick()
        waitFor("delete-account")
        tag("delete-account").performScrollTo().performClick()
        waitFor("delete-account-confirm")
        tag("delete-account-confirm").performClick()
        waitFor("welcome")
        assertTrue(fakeAuth.deleted)
        assertEquals(AppSettings(), fakeSettings.settings.value)
        tag("profile").assertDoesNotExist()
        assertBackLeavesTheApp()
    }

    @Test
    fun `a relaunch keeps the map home`() {
        waitFor("welcome")
        tag("later").performClick()
        waitFor("locate")
        compose.activityRule.scenario.recreate()
        waitFor("locate")
        tag("welcome").assertDoesNotExist()
    }

    @Test
    fun `a relaunch keeps the guide's home, and the screen open on it`() {
        start(AppMode.GUIDE, signedIn = true)
        waitFor("profile")
        tag("settings").performClick()
        waitFor("settings-screen")
        compose.activityRule.scenario.recreate()
        waitFor("settings-screen")
        tag("back").performClick()
        waitFor("profile")
        waitForNo("settings-screen")
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
