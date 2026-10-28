package ca.kallistesoft.guidehunter.feature.becomeguide

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeNativeAds
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeRequestRepository
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.ui.day
import ca.kallistesoft.guidehunter.core.ui.tripDates
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp")
class RequestsReceivedScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val uid = TestGuides.user.uid
    private val requests = FakeRequestRepository()
    private val nativeAds = FakeNativeAds()
    private val account = FakeGuideAccountRepository()
    private val opened = mutableListOf<String>()
    private val pending = TestGuides.request.copy(id = "bear_u2", guideUid = uid, userUid = "u2")

    private fun launch() {
        val viewModel = RequestsReceivedViewModel(FakeAuthRepository(TestGuides.user), requests, account, nativeAds)
        compose.setContent {
            GuideHunterTheme {
                RequestsReceivedScreen(onBack = { opened += "back" }, onEditProfile = { opened += "profile" }, viewModel = viewModel)
            }
        }
    }

    @Test
    fun `no request yet says so`() {
        launch()
        compose.onNodeWithTag("no-requests").assertExists()
        compose.onNodeWithTag("back").performClick()
        assertEquals(listOf("back"), opened)
    }

    @Test
    fun `a pending request shows who, how to reach them and when, and is accepted`() {
        account.profiles.value = mapOf(uid to TestGuides.profile)
        requests.give(pending, pending.copy(id = "bear_u3", userUid = "u3", status = RequestStatus.REJECTED, user = pending.user.copy(name = "Sam")))
        launch()
        compose.onNodeWithText(app.getString(R.string.requests_waiting_header, 1).uppercase()).assertExists()
        compose.onNodeWithText("Bear hunt · ${app.tripDates(pending.trip!!)}").assertExists()
        compose.onNodeWithText(app.getString(R.string.request_received_on, app.day(pending.createdAt))).assertExists()
        compose.onNodeWithTag("answered-bear_u3").assertExists()
        compose.onNodeWithTag("call-bear_u2").performClick()
        assertEquals(Intent.ACTION_DIAL, shadowOf(app).nextStartedActivity.action)
        compose.onNodeWithTag("email-bear_u2").performClick()
        assertEquals("Bear hunt", shadowOf(app).nextStartedActivity.getStringExtra(Intent.EXTRA_SUBJECT))
        compose.onNodeWithTag("accept-bear_u2").performClick()
        compose.waitForIdle()
        assertEquals(RequestStatus.APPROVED, requests.requests.value.getValue("bear_u2").status)
        compose.onNodeWithTag("answered-bear_u2").assertExists()
    }

    @Test
    fun `declining asks to confirm first`() {
        requests.give(pending.copy(user = pending.user.copy(phone = "")))
        launch()
        compose.onNodeWithTag("call-bear_u2").assertDoesNotExist()
        compose.onNodeWithTag("decline-bear_u2").performClick()
        compose.onNodeWithText(app.getString(R.string.decline_title, pending.user.name)).assertExists()
        compose.onNodeWithTag("decline-cancel").performClick()
        assertEquals(RequestStatus.PENDING, requests.requests.value.getValue("bear_u2").status)
        compose.onNodeWithTag("decline-bear_u2").performClick()
        compose.onNodeWithTag("decline-confirm").performClick()
        compose.waitForIdle()
        assertEquals(RequestStatus.REJECTED, requests.requests.value.getValue("bear_u2").status)
    }

    @Test
    fun `accepting without a profile leads to it, and a failure says so`() {
        requests.give(pending)
        launch()
        compose.onNodeWithTag("accept-bear_u2").performClick()
        compose.onNodeWithText(app.getString(R.string.requests_needs_profile)).assertExists()
        compose.onNodeWithTag("edit-profile").performClick()
        assertEquals(listOf("profile"), opened)
        requests.failure = IllegalStateException("offline")
        compose.onNodeWithTag("decline-bear_u2").performClick()
        compose.onNodeWithTag("decline-confirm").performClick()
        compose.onNodeWithText(app.getString(R.string.requests_change_failed)).assertExists()
    }

    @Test
    fun `an ad card after the pending requests, none without any`() {
        requests.give(pending)
        launch()
        compose.onNodeWithTag("requests-received-list").performScrollToNode(hasTestTag("native-ad"))
        compose.onNodeWithTag("ad-label", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `only answered requests, no ad card`() {
        requests.give(pending.copy(status = RequestStatus.CANCELLED))
        launch()
        compose.onNodeWithTag("answered-bear_u2").assertExists()
        compose.onNodeWithTag("native-ad").assertDoesNotExist()
    }
}
