package ca.kallistesoft.guidehunter.feature.map

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.ui.R as UiR
import ca.kallistesoft.guidehunter.core.ui.day
import ca.kallistesoft.guidehunter.core.model.Trip
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.ui.LinkOpener
import ca.kallistesoft.guidehunter.core.ui.tripDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Each contact state of a guide's offer, and what its button does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp")
class GuideDetailTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val events = mutableListOf<String>()

    private val trips = TestGuides.bear.schedule.trips(TestGuides.today)
    private val chosen = mutableListOf<Trip>()

    private val actions = ContactActions(
        unlock = { events += "unlock" },
        interested = { events += "interested" },
        cancel = { events += "cancel" },
        myRequests = { events += "my-requests" },
        settings = { events += "settings" },
    )

    private fun show(
        contact: ContactState,
        item: GuideItem = GuideItem(TestGuides.bear, distanceKm = 470),
        trips: List<Trip> = item.guide.schedule.trips(TestGuides.today),
        trip: Trip? = null,
        busy: Boolean = false,
        error: RequestError? = null,
        featured: Boolean = false,
    ) {
        compose.setContent {
            GuideHunterTheme { GuideDetail(SelectedGuide(item, contact, trips, trip, featured, busy = busy, error = error), actions, LinkOpener(app), { chosen += it }) }
        }
    }

    private fun requested(status: RequestStatus) = ContactState.Requested(
        TestGuides.request.copy(status = status, guideContact = TestGuides.bearContact.takeIf { status == RequestStatus.APPROVED }),
    )

    @Test
    fun `the offer shows its price, place and distance`() {
        show(ContactState.Locked)
        compose.onNodeWithText(app.getString(R.string.price_from, "CA$2,400"), useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Abitibi · " + app.getString(R.string.distance_km, "470")).assertExists()
        compose.onNodeWithText(app.getString(ca.kallistesoft.guidehunter.core.ui.R.string.species_black_bear)).assertExists()
    }

    @Test
    fun `locked, the ad is offered`() {
        show(ContactState.Locked)
        compose.onNodeWithText(app.getString(R.string.contact_locked)).performScrollTo()
        compose.onNodeWithTag("unlock").performScrollTo().performClick()
        assertEquals(listOf("unlock"), events)
    }

    @Test
    fun `the ad showing, or the request being read, shows progress`() {
        show(ContactState.Unlocking)
        compose.onNodeWithTag("contact-loading").performScrollTo()
        compose.onNodeWithTag("unlock").assertDoesNotExist()
    }

    @Test
    fun `an ad closed early offers the ad again`() {
        show(ContactState.Failed(UnlockError.NOT_EARNED))
        compose.onNodeWithText(app.getString(R.string.contact_not_earned)).performScrollTo()
        compose.onNodeWithTag("unlock").performScrollTo().performClick()
        assertEquals(listOf("unlock"), events)
    }

    @Test
    fun `offline says so`() {
        show(ContactState.Failed(UnlockError.OFFLINE))
        compose.onNodeWithText(app.getString(R.string.contact_offline)).performScrollTo()
        compose.onNodeWithTag("unlock").performScrollTo().performClick()
        assertEquals("the ad is offered again once online", listOf("unlock"), events)
    }

    @Test
    fun `after the ad, I'm interested, with the chosen trip`() {
        show(ContactState.Unlocked, trip = trips[1])
        compose.onNodeWithText(app.getString(R.string.interested_note)).performScrollTo()
        compose.onNodeWithText(app.getString(UiR.string.your_trip, app.tripDates(trips[1]))).performScrollTo()
        compose.onNodeWithTag("interested").performScrollTo().performClick()
        assertEquals(listOf("interested"), events)
    }

    @Test
    fun `while signing in or sending, I'm interested waits, errors say what failed`() {
        show(ContactState.Unlocked, busy = true, error = RequestError.SIGN_IN_FAILED)
        compose.onNodeWithTag("interested").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(app.getString(R.string.error_sign_in)).performScrollTo()
    }

    @Test
    fun `a request that couldn't be sent says so`() {
        show(ContactState.Unlocked, error = RequestError.SEND_FAILED)
        compose.onNodeWithText(app.getString(R.string.error_send)).performScrollTo()
    }

    @Test
    fun `a build without Firebase says requests can't be sent`() {
        show(requested(RequestStatus.CANCELLED), error = RequestError.NOT_CONFIGURED)
        compose.onNodeWithText(app.getString(R.string.error_not_configured)).performScrollTo()
    }

    @Test
    fun `a pending request, its date, my requests and cancel`() {
        show(requested(RequestStatus.PENDING))
        compose.onNodeWithTag("status-PENDING").performScrollTo()
        compose.onNodeWithText(app.getString(R.string.request_pending, app.day(TestGuides.request.createdAt))).performScrollTo()
        compose.onNodeWithText(app.getString(UiR.string.your_trip, app.tripDates(TestGuides.request.trip!!))).performScrollTo()
        compose.onNodeWithTag("my-requests").performScrollTo().performClick()
        compose.onNodeWithTag("cancel-request").performScrollTo().performClick()
        assertEquals(listOf("my-requests", "cancel"), events)
        compose.onNodeWithTag("unlock").assertDoesNotExist()
    }

    @Test
    fun `an accepted request shows the guide's contact, whose links open`() {
        show(requested(RequestStatus.APPROVED))
        compose.onNodeWithTag("status-APPROVED").performScrollTo()
        compose.onNodeWithTag("contact-name").performScrollTo()
        compose.onNodeWithText(TestGuides.bearContact.name).assertExists()
        compose.onNodeWithTag("contact-email").performScrollTo().performClick()
        val email = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_SENDTO, email.action)
        assertTrue(email.getStringExtra(Intent.EXTRA_SUBJECT)!!.endsWith("Bear hunt · ${app.tripDates(TestGuides.request.trip!!)}"))
        compose.onNodeWithTag("contact-phone").performScrollTo().performClick()
        assertEquals(Intent.ACTION_DIAL, shadowOf(app).nextStartedActivity.action)
    }

    @Test
    fun `a declined request, or an offer removed, only says so`() {
        show(requested(RequestStatus.REJECTED))
        compose.onNodeWithText(app.getString(R.string.request_rejected)).performScrollTo()
        compose.onNodeWithTag("cancel-request").assertDoesNotExist()
        compose.onNodeWithTag("send-again").assertDoesNotExist()
    }

    @Test
    fun `an offer removed says so`() {
        show(requested(RequestStatus.OFFER_REMOVED))
        compose.onNodeWithText(app.getString(R.string.request_offer_removed)).performScrollTo()
        compose.onNodeWithTag("status-OFFER_REMOVED").assertExists()
    }

    @Test
    fun `a cancelled request can be sent again`() {
        show(requested(RequestStatus.CANCELLED))
        compose.onNodeWithText(app.getString(R.string.request_cancelled)).performScrollTo()
        compose.onNodeWithTag("send-again").performScrollTo().performClick()
        assertEquals(listOf("interested"), events)
    }

    @Test
    fun `the account's own offer offers guide mode, no trip`() {
        show(ContactState.Own, trip = trips[0])
        compose.onNodeWithText(app.getString(R.string.own_offer)).performScrollTo()
        compose.onNodeWithTag("your-trip").assertDoesNotExist()
        compose.onNodeWithTag("own-switch").performScrollTo().performClick()
        assertEquals(listOf("settings"), events)
    }

    @Test
    fun `the next departure shows, the others open under other dates, and each can be chosen`() {
        show(ContactState.Locked)
        compose.onNodeWithTag("trip-length").assertExists()
        compose.onNodeWithText("1 week").assertExists()
        compose.onNodeWithTag("trip-2027-05-01").performScrollTo().performClick()
        compose.onNodeWithTag("trip-2027-05-15").assertDoesNotExist()
        compose.onNodeWithTag("other-dates").performScrollTo().performClick()
        compose.onNodeWithTag("trip-2027-06-05").performScrollTo().performClick()
        assertEquals(listOf(trips[0], trips[2]), chosen)
    }

    @Test
    fun `a chosen later trip opens the other dates, and goes with the request`() {
        show(ContactState.Unlocked, trip = trips[1])
        compose.onNodeWithTag("trip-2027-05-15").assertExists()
        compose.onNodeWithTag("your-trip").performScrollTo()
        compose.onNodeWithText(app.getString(UiR.string.your_trip, app.tripDates(trips[1]))).assertExists()
    }

    @Test
    fun `half days show their half`() {
        show(ContactState.Locked, GuideItem(TestGuides.salmon))
        compose.onNodeWithText(app.getString(ca.kallistesoft.guidehunter.core.ui.R.string.trip_half_day)).assertExists()
        compose.onNodeWithTag("trip-2027-06-10-MORNING").assertExists()
        compose.onNodeWithTag("other-dates").assertExists()
    }

    @Test
    fun `dates on request`() {
        show(ContactState.Locked, GuideItem(TestGuides.moose))
        compose.onNodeWithTag("dates-on-request").assertExists()
        compose.onNodeWithTag("other-dates").assertDoesNotExist()
        compose.onNodeWithTag("your-trip").assertDoesNotExist()
    }
}
