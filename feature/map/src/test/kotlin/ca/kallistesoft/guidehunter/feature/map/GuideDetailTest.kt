package ca.kallistesoft.guidehunter.feature.map

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.model.GuideContact
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
    private var unlocks = 0

    private val trips = TestGuides.bear.schedule.trips(TestGuides.today)
    private val chosen = mutableListOf<Trip>()

    private fun show(
        contact: ContactState,
        item: GuideItem = GuideItem(TestGuides.bear, distanceKm = 470),
        trips: List<Trip> = item.guide.schedule.trips(TestGuides.today),
        trip: Trip? = null,
    ) {
        compose.setContent {
            GuideHunterTheme { GuideDetail(SelectedGuide(item, contact, trips, trip), { unlocks++ }, LinkOpener(app), { chosen += it }) }
        }
    }

    @Test
    fun `the offer shows its price, place and distance`() {
        show(ContactState.Locked)
        compose.onNodeWithText(app.getString(R.string.price_from, "CA$2,400"), useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Abitibi · " + app.getString(R.string.distance_km, "470")).assertExists()
        compose.onNodeWithText(app.getString(ca.kallistesoft.guidehunter.core.ui.R.string.species_black_bear)).assertExists()
    }

    @Test
    fun `the hidden contact shows placeholder bars, gone once unlocked`() {
        show(ContactState.Locked)
        compose.onNodeWithTag("contact-placeholder", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `loading shows progress`() {
        show(ContactState.Unlocking)
        compose.onNodeWithText(app.getString(R.string.contact_unlocking)).performScrollTo()
        compose.onNodeWithTag("contact-loading").assertExists()
        compose.onNodeWithTag("unlock").assertDoesNotExist()
    }

    @Test
    fun `an ad closed early offers the ad again`() {
        show(ContactState.Failed(UnlockError.NOT_EARNED))
        compose.onNodeWithText(app.getString(R.string.contact_not_earned)).performScrollTo()
        compose.onNodeWithTag("unlock").performScrollTo().performClick()
        assertEquals(1, unlocks)
    }

    @Test
    fun `offline says so`() {
        show(ContactState.Failed(UnlockError.OFFLINE))
        compose.onNodeWithText(app.getString(R.string.contact_offline)).performScrollTo()
        compose.onNodeWithTag("unlock").performScrollTo().performClick()
        assertEquals("the ad is offered again once online", 1, unlocks)
    }

    @Test
    fun `a failed load is tried again without an ad`() {
        show(ContactState.Failed(UnlockError.LOAD_FAILED))
        compose.onNodeWithText(app.getString(R.string.contact_load_failed)).performScrollTo()
        compose.onNodeWithText(app.getString(R.string.try_again)).performScrollTo().performClick()
        assertEquals(1, unlocks)
    }

    @Test
    fun `a guide without contact details says so`() {
        show(ContactState.Missing)
        compose.onNodeWithText(app.getString(R.string.contact_missing)).performScrollTo()
    }

    @Test
    fun `only the contact details given are shown`() {
        show(ContactState.Unlocked(GuideContact("Camp")), GuideItem(TestGuides.salmon))
        compose.onNodeWithTag("contact-name").performScrollTo()
        compose.onNodeWithTag("contact-placeholder", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("contact-phone").assertDoesNotExist()
        compose.onNodeWithTag("contact-email").assertDoesNotExist()
        compose.onNodeWithTag("contact-website").assertDoesNotExist()
        compose.onNodeWithText("Gaspé").assertExists()
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
    fun `a chosen later trip opens the other dates, and goes with the contact and the email`() {
        show(ContactState.Unlocked(TestGuides.bearContact), trip = trips[1])
        compose.onNodeWithTag("trip-2027-05-15").assertExists()
        val dates = app.tripDates(trips[1])
        compose.onNodeWithTag("your-trip").performScrollTo()
        compose.onNodeWithText(app.getString(R.string.your_trip, dates)).assertExists()
        compose.onNodeWithTag("contact-email").performScrollTo().performClick()
        val email = shadowOf(app).nextStartedActivity
        assertTrue(email.getStringExtra(Intent.EXTRA_SUBJECT)!!.endsWith("Bear hunt · $dates"))
        assertEquals(app.getString(R.string.contact_email_body, "Bear hunt", dates), email.getStringExtra(Intent.EXTRA_TEXT))
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
