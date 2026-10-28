package ca.kallistesoft.guidehunter.core.ui

import android.app.Application
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.model.Trip
import ca.kallistesoft.guidehunter.core.model.UserContact
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp")
class RequestsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val events = mutableListOf<String>()
    private val trip = Trip(LocalDate(2027, 5, 18), LocalDate(2027, 5, 21))
    private val alex = UserContact("Alex Tremblay", "alex@example.com", "+1 418 555 0199")

    @Test
    fun `each status has its chip`() {
        compose.setContent { MaterialTheme { androidx.compose.foundation.layout.Column { RequestStatus.entries.forEach { StatusChip(it) } } } }
        listOf("Pending", "Accepted", "Declined", "Cancelled", "Offer removed").forEach { compose.onNodeWithText(it).assertExists() }
        RequestStatus.entries.forEach { compose.onNodeWithTag("status-${it.name}").assertExists() }
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the statuses in French`() {
        assertEquals(
            listOf("En attente", "Acceptée", "Refusée", "Annulée", "Offre retirée"),
            RequestStatus.entries.map { app.getString(it.label) },
        )
    }

    @Test
    fun `a day and a request's trip`() {
        assertNotEquals("", app.day(0))
        assertEquals(app.tripDates(trip), app.requestTrip(trip))
        assertEquals("Dates on request", app.requestTrip(null))
    }

    @Test
    fun `the confirmation shows the offer, the trip and what's shared, and its buttons answer`() {
        compose.setContent {
            MaterialTheme {
                ConfirmRequestSheet("Bear hunt", trip, alex, sending = false, { events += "send" }, { events += "edit" }, { events += "dismiss" })
            }
        }
        compose.onNodeWithText("Bear hunt").assertExists()
        compose.onNodeWithText(app.getString(R.string.your_trip, app.tripDates(trip))).assertExists()
        listOf(alex.name, alex.email, alex.phone).forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithTag("edit-contact").performScrollTo().performClick()
        compose.onNodeWithTag("send-request").performScrollTo().performClick()
        compose.onNodeWithTag("not-now").performScrollTo().performClick()
        assertEquals(listOf("edit", "send", "dismiss"), events)
    }

    @Test
    fun `while sending, the confirmation waits, here with dates on request and no phone`() {
        compose.setContent {
            MaterialTheme { ConfirmRequest("Moose", null, alex.copy(phone = ""), sending = true, {}, {}, {}) }
        }
        compose.onNodeWithText("Dates on request").assertExists()
        compose.onNodeWithTag("send-request").assertIsNotEnabled()
        compose.onNodeWithTag("not-now").assertIsNotEnabled()
    }

    @Test
    fun `a guide's contact links open the dialer, the mail and the website`() {
        val contact = GuideContact("Camp", phone = "819 555 0100", email = "camp@example.com", website = "example.com")
        compose.setContent { MaterialTheme { androidx.compose.foundation.layout.Column { ContactDetails(contact, LinkOpener(app), "Bear hunt", trip) } } }
        compose.onNodeWithText("Camp").assertExists()
        compose.onNodeWithTag("contact-phone").performClick()
        assertEquals(Intent.ACTION_DIAL, shadowOf(app).nextStartedActivity.action)
        compose.onNodeWithTag("contact-email").performClick()
        val email = shadowOf(app).nextStartedActivity
        val dates = app.tripDates(trip)
        assertEquals("Trip found on GuideHunter · Bear hunt · $dates", email.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals(app.getString(R.string.contact_email_body, "Bear hunt", dates), email.getStringExtra(Intent.EXTRA_TEXT))
        compose.onNodeWithTag("contact-website").performClick()
        assertEquals("https", shadowOf(app).nextStartedActivity.data?.scheme)
    }

    @Test
    fun `only the contact given shows`() {
        compose.setContent { MaterialTheme { androidx.compose.foundation.layout.Column { ContactDetails(GuideContact("Camp"), LinkOpener(app), "Moose", null) } } }
        compose.onNodeWithTag("contact-phone").assertDoesNotExist()
        compose.onNodeWithTag("contact-email").assertDoesNotExist()
        compose.onNodeWithTag("contact-website").assertDoesNotExist()
    }
}
