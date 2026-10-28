package ca.kallistesoft.guidehunter.feature.account

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeNativeAds
import ca.kallistesoft.guidehunter.core.testing.FakeRequestRepository
import ca.kallistesoft.guidehunter.core.testing.FakeUserAccountRepository
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.ui.R as UiR
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
class MyRequestsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val uid = TestGuides.user.uid
    private val auth = FakeAuthRepository(TestGuides.user)
    private val requests = FakeRequestRepository()
    private val nativeAds = FakeNativeAds()
    private val users = FakeUserAccountRepository()
    private var backs = 0
    private var contacts = 0

    private fun launch() {
        compose.setContent {
            GuideHunterTheme {
                MyRequestsScreen(onBack = { backs++ }, onMyContact = { contacts++ }, viewModel = MyRequestsViewModel(auth, requests, users, nativeAds))
            }
        }
    }

    private val list get() = compose.onNodeWithTag("my-requests-list")

    private fun request(offer: String, status: RequestStatus, updatedAt: Long) = TestGuides.request.copy(
        id = "${offer}_$uid",
        offerId = offer,
        offerTitle = "Offer $offer",
        status = status,
        createdAt = updatedAt * DAY,
        updatedAt = updatedAt * DAY,
        guideContact = TestGuides.bearContact.takeIf { status == RequestStatus.APPROVED },
    )

    @Test
    fun `no request yet says so`() {
        launch()
        compose.onNodeWithTag("no-requests").assertExists()
        compose.onNodeWithTag("back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `every status shows, with its date and what can be done`() {
        requests.give(
            request("a", RequestStatus.PENDING, 6),
            request("b", RequestStatus.APPROVED, 5),
            request("c", RequestStatus.REJECTED, 4),
            request("d", RequestStatus.CANCELLED, 3),
            request("e", RequestStatus.OFFER_REMOVED, 2),
        )
        launch()
        val trip = app.tripDates(TestGuides.request.trip!!)
        compose.onNodeWithText("$trip · " + app.getString(R.string.request_sent_on, app.day(6 * DAY))).assertExists()
        compose.onNodeWithText("$trip · " + app.getString(R.string.request_accepted_on, app.day(5 * DAY))).assertExists()
        list.performScrollToNode(hasTestTag("contact-phone"))
        compose.onNodeWithTag("contact-phone").performClick()
        assertEquals(Intent.ACTION_DIAL, shadowOf(app).nextStartedActivity.action)
        list.performScrollToNode(hasText(app.getString(R.string.request_rejected_note)))
        list.performScrollToNode(hasText("$trip · " + app.getString(R.string.request_cancelled_on, app.day(3 * DAY))))
        list.performScrollToNode(hasText(app.getString(R.string.request_removed_note)))
        compose.onNodeWithTag("cancel-c_$uid").assertDoesNotExist()

        list.performScrollToNode(hasTestTag("cancel-a_$uid"))
        compose.onNodeWithTag("cancel-a_$uid").performClick()
        compose.waitForIdle()
        assertEquals(RequestStatus.CANCELLED, requests.requests.value.getValue("a_$uid").status)
    }

    @Test
    fun `send again confirms, edits the contact or sends`() {
        requests.give(request("d", RequestStatus.CANCELLED, 3).copy(trip = null))
        launch()
        compose.onNodeWithText(app.getString(UiR.string.dates_on_request), substring = true).assertExists()
        compose.onNodeWithTag("send-again-d_$uid").performClick()
        compose.onNodeWithTag("edit-contact").performScrollTo().performClick()
        assertEquals(1, contacts)
        compose.onNodeWithTag("send-request").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(RequestStatus.PENDING, requests.requests.value.getValue("d_$uid").status)
    }

    @Test
    fun `a failed change says so, and an incomplete contact opens the form`() {
        auth.user.value = TestGuides.user.copy(name = "")
        requests.give(request("a", RequestStatus.PENDING, 6), request("d", RequestStatus.CANCELLED, 3))
        requests.failure = IllegalStateException("offline")
        launch()
        compose.onNodeWithTag("cancel-a_$uid").performClick()
        compose.onNodeWithTag("request-error").assertExists()
        list.performScrollToNode(hasTestTag("send-again-d_$uid"))
        compose.onNodeWithTag("send-again-d_$uid").performClick()
        compose.waitForIdle()
        assertEquals(1, contacts)
    }

    @Test
    fun `one ad card after the second request, or after the only one, none without requests`() {
        requests.give(request("a", RequestStatus.PENDING, 6))
        launch()
        list.performScrollToNode(hasTestTag("native-ad"))
        compose.onNodeWithTag("ad-label", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `with no request there's no ad card`() {
        launch()
        compose.onNodeWithTag("native-ad").assertDoesNotExist()
    }

    private companion object {
        /** A day apart, so each request's date differs. */
        const val DAY = 86_400_000L
    }
}
