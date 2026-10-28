package ca.kallistesoft.guidehunter

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import ca.kallistesoft.guidehunter.core.data.AuthRepository
import ca.kallistesoft.guidehunter.core.data.GuideAccountRepository
import ca.kallistesoft.guidehunter.core.data.GuideRepository
import ca.kallistesoft.guidehunter.core.data.LocationRepository
import ca.kallistesoft.guidehunter.core.data.SettingsRepository
import ca.kallistesoft.guidehunter.core.data.di.DataModule
import ca.kallistesoft.guidehunter.core.auth.GoogleSignIn
import ca.kallistesoft.guidehunter.core.auth.GoogleSignInModule
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.model.AppSettings
import ca.kallistesoft.guidehunter.core.model.GuideOffer
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The guide's screens in the real app, with fake repositories and Google sheet, signed in and in guide mode: the
 * offers are the home, and the profile, the offer form and the settings open from them and come back.
 */
@HiltAndroidTest
@UninstallModules(DataModule::class, GoogleSignInModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "en-w411dp-h891dp")
class GuideAccountNavigationTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val uid = TestGuides.user.uid

    @BindValue @JvmField
    val auth: AuthRepository = FakeAuthRepository(signedIn = TestGuides.user)

    @BindValue @JvmField
    val settings: SettingsRepository = FakeSettingsRepository(AppSettings(onboarded = true, mode = AppMode.GUIDE))

    @BindValue @JvmField
    val account: GuideAccountRepository = FakeGuideAccountRepository().apply {
        give(uid, GuideOffer("o1", TestGuides.offer))
    }

    @BindValue @JvmField
    val guides: GuideRepository = FakeGuideRepository()

    @BindValue @JvmField
    val location: LocationRepository = FakeLocationRepository()

    @BindValue @JvmField
    val google: GoogleSignIn = FakeGoogleSignIn()

    @Before
    fun setUp() = hilt.inject()

    private fun tag(tag: String) = compose.onNodeWithTag(tag)

    private fun waitFor(tag: String) = compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `from the offers, fill in the profile, open an offer, add one, and back on the offers leaves the app`() {
        waitFor("profile")

        tag("edit-profile").performClick()
        waitFor("field-business-name")
        tag("field-business-name").performTextInput("Pourvoirie Test")
        tag("save").performScrollTo().performClick()
        waitFor("offer-o1")
        assertEquals("Pourvoirie Test", (account as FakeGuideAccountRepository).profiles.value[uid]?.businessName)

        tag("offer-o1").performClick()
        waitFor("delete-offer")
        tag("back").performClick()

        tag("add-offer").performScrollTo().performClick()
        waitFor("field-title")
        tag("next").performClick() // empty: errors, stays on the first step
        tag("field-title").assertExists()
        tag("back").performClick()

        waitFor("offer-o1")
        tag("back").assertDoesNotExist() // the guide's home: no back arrow
        // Back on the offers (the only entry) leaves the app, as Android does.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.activityRule.scenario.onActivity { assertTrue(it.isFinishing) }
    }

    @Test
    fun `the guide's profile opens from the settings and returns to them`() {
        waitFor("profile")
        tag("settings").performClick()
        waitFor("guide-profile")
        tag("guide-profile").performClick()
        waitFor("field-business-name")
        tag("back").performClick()
        waitFor("settings-screen")
        tag("back").performClick()
        waitFor("offer-o1")
    }

    @Test
    fun `an offer sent without a profile leads to the profile`() {
        waitFor("offer-o1")
        tag("offer-o1").performClick()
        waitFor("send") // an edit opens on its summary
        tag("send").performClick()
        waitFor("needs-profile")
        tag("needs-profile").performScrollTo().performClick()
        waitFor("field-business-name")
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
