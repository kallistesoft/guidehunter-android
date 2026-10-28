package ca.kallistesoft.guidehunter

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import ca.kallistesoft.guidehunter.core.auth.GoogleSignIn
import ca.kallistesoft.guidehunter.core.auth.GoogleSignInModule
import ca.kallistesoft.guidehunter.core.data.AuthRepository
import ca.kallistesoft.guidehunter.core.data.GuideAccountRepository
import ca.kallistesoft.guidehunter.core.data.GuideRepository
import ca.kallistesoft.guidehunter.core.data.LocationRepository
import ca.kallistesoft.guidehunter.core.data.RequestRepository
import ca.kallistesoft.guidehunter.core.data.SettingsRepository
import ca.kallistesoft.guidehunter.core.data.UserAccountRepository
import ca.kallistesoft.guidehunter.core.data.di.DataModule
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.model.AppSettings
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGoogleSignIn
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGuideRepository
import ca.kallistesoft.guidehunter.core.testing.FakeLocationRepository
import ca.kallistesoft.guidehunter.core.testing.FakeRequestRepository
import ca.kallistesoft.guidehunter.core.testing.FakeSettingsRepository
import ca.kallistesoft.guidehunter.core.testing.FakeUserAccountRepository
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.ui.R as UiR
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Requests across the app, with fakes: a user's "I'm interested" from the map to "My requests" (and the contact form
 * on the way), the settings' rows, and a guide's requests received from their home and settings.
 */
@HiltAndroidTest
@UninstallModules(DataModule::class, GoogleSignInModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "en-w411dp-h891dp")
class RequestsNavigationTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val uid = TestGuides.user.uid
    private val fakeAuth = FakeAuthRepository(signedIn = TestGuides.user)
    private val fakeSettings = FakeSettingsRepository(AppSettings(onboarded = true))
    private val fakeRequests = FakeRequestRepository()
    private val fakeUsers = FakeUserAccountRepository()

    @BindValue @JvmField
    val auth: AuthRepository = fakeAuth

    @BindValue @JvmField
    val settings: SettingsRepository = fakeSettings

    @BindValue @JvmField
    val requests: RequestRepository = fakeRequests

    @BindValue @JvmField
    val users: UserAccountRepository = fakeUsers

    @BindValue @JvmField
    val account: GuideAccountRepository = FakeGuideAccountRepository(fakeRequests)

    @BindValue @JvmField
    val guides: GuideRepository = FakeGuideRepository()

    @BindValue @JvmField
    val location: LocationRepository = FakeLocationRepository()

    @BindValue @JvmField
    val google: GoogleSignIn = FakeGoogleSignIn()

    @Before
    fun setUp() = hilt.inject()

    private fun tag(tag: String) = compose.onNodeWithTag(tag)

    private fun waitFor(tag: String) =
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `interested on the map, the contact filled in on the way, sent, then found in my requests`() {
        fakeAuth.user.value = TestGuides.user.copy(name = "")
        waitFor("guide-bear")
        tag("guide-bear").performClick()
        tag("unlock").performScrollTo().performClick()
        tag("interested").performScrollTo().performClick()

        waitFor("my-contact-screen")
        tag("field-name").performTextInput("Jo Hunter")
        tag("save").performScrollTo().performClick()

        // Back on the offer, the confirmation follows.
        waitFor("send-request")
        tag("send-request").performScrollTo().performClick()
        waitFor("request-sent")
        val myRequests = RuntimeEnvironment.getApplication().getString(UiR.string.my_requests)
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText(myRequests) and hasAnyAncestor(hasTestTag("request-sent"))).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText(myRequests) and hasAnyAncestor(hasTestTag("request-sent"))).performClick()
        waitFor("my-requests-list")
        tag("request-bear_$uid").assertExists()
        assertEquals("Jo Hunter", fakeRequests.requests.value.getValue("bear_$uid").user.name)
    }

    @Test
    fun `the settings open my contact info and my requests, and return to them`() {
        waitFor("settings")
        tag("settings").performClick()
        waitFor("my-contact")
        tag("my-contact").performScrollTo().performClick()
        waitFor("my-contact-screen")
        tag("back").performClick()
        waitFor("my-requests")
        tag("my-requests").performScrollTo().performClick()
        waitFor("no-requests")
        tag("back").performClick()
        waitFor("settings-screen")
    }

    @Test
    fun `a guide opens the requests received from their home and settings, and accepting without a profile leads to it`() {
        fakeSettings.settings.value = AppSettings(onboarded = true, mode = AppMode.GUIDE)
        fakeRequests.give(TestGuides.request.copy(id = "o1_u2", offerId = "o1", guideUid = uid, userUid = "u2"))
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag("requests-badge"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        tag("requests-received").performClick()
        waitFor("accept-o1_u2")
        tag("accept-o1_u2").performClick()
        waitFor("edit-profile")
        tag("edit-profile").performClick()
        waitFor("field-business-name")
        tag("back").performClick()
        waitFor("accept-o1_u2")
        tag("back").performClick()
        waitFor("settings")
        tag("settings").performClick()
        waitFor("requests-received")
        tag("requests-received").performScrollTo().performClick()
        waitFor("accept-o1_u2")
        assertEquals(RequestStatus.PENDING, fakeRequests.requests.value.getValue("o1_u2").status)
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
