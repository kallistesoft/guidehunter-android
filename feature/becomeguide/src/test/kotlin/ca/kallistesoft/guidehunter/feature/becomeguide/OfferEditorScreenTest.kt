package ca.kallistesoft.guidehunter.feature.becomeguide

import android.Manifest
import android.app.Application
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import ca.kallistesoft.guidehunter.core.model.OfferStatus
import ca.kallistesoft.guidehunter.core.model.Schedule
import ca.kallistesoft.guidehunter.core.model.TripLength
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeLocationRepository
import ca.kallistesoft.guidehunter.core.testing.FakeRequestRepository
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.ui.departureDate
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The offer form with a stand-in map picker (a tap places the pin at [PICKED]); Google Maps can't draw on the JVM. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp")
class OfferEditorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val uid = TestGuides.user.uid
    private val auth = FakeAuthRepository(TestGuides.user)
    private val requests = FakeRequestRepository()
    private val account = FakeGuideAccountRepository(requests).apply { profiles.value = mapOf(uid to TestGuides.profile) }
    private val location = FakeLocationRepository()
    private var backs = 0
    private var dones = 0
    private var profileOpened = 0

    /** What the system answers to the location permission request. */
    private var permissionAnswer = false

    private fun str(id: Int, vararg args: Any) = app.getString(id, *args)

    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: androidx.core.app.ActivityOptionsCompat?) {
            if (permissionAnswer) shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
            dispatchResult(requestCode, permissionAnswer as O)
        }
    }

    private fun launch(offerId: String? = null) {
        val viewModel = OfferEditorViewModel(offerId, auth, account, location, requests, SavedStateHandle(), { TestGuides.today })
        compose.setContent {
            val owner = object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                GuideHunterTheme {
                    OfferEditorScreen(
                        offerId = offerId,
                        onBack = { backs++ },
                        onDone = { dones++ },
                        onEditProfile = { profileOpened++ },
                        viewModel = viewModel,
                        picker = { _, onPick, modifier -> Box(modifier.clickable { onPick(PICKED) }) },
                    )
                }
            }
        }
    }

    private fun type(tag: String, text: String) = compose.onNodeWithTag(tag).performScrollTo().performTextInput(text)

    private fun next() = compose.onNodeWithTag("next").performClick()

    private fun step(title: Int) = compose.onNodeWithTag("step-title").assertTextEquals(str(title))

    /** Steps 1 to 4 as the guide fills them in, up to the summary: a week, dates on request. */
    private fun fillIn() {
        type("field-title", TestGuides.offer.title)
        compose.onNodeWithTag("activity-HUNT").performScrollTo().performClick()
        type("field-description", TestGuides.offer.description)
        next()
        compose.onNodeWithTag("species-BLACK_BEAR").performScrollTo().performClick()
        next()
        type("field-region", "Abitibi")
        compose.onNodeWithTag("location-picker").performScrollTo().performClick()
        next()
        compose.onNodeWithTag("length-7").performScrollTo().performClick()
        next()
        type("field-price", "2400")
    }

    /** What [fillIn] sends: the test offer without its dates. */
    private val filled = TestGuides.offer.copy(location = PICKED, schedule = Schedule(TripLength.Days(7)))

    private fun sent(): OfferDraft = account.offersByUser.value.getValue(uid).single().draft

    @Test
    fun `a new offer goes through five steps, then is sent for review and the screen closes`() {
        launch()
        step(R.string.step_what)
        compose.onNodeWithText(str(R.string.step_of, 1, 5)).assertExists()
        compose.onNodeWithText(str(R.string.offer_intro)).assertExists()
        compose.onNodeWithTag("delete-offer").assertDoesNotExist()
        compose.onNodeWithTag("step-back").assertDoesNotExist()
        fillIn()
        step(R.string.step_review)
        compose.onNodeWithTag("summary-WHEN").performScrollTo()
        compose.onNodeWithText(str(R.string.dates_on_request)).assertExists()
        compose.onNodeWithTag("send").performClick()
        compose.waitForIdle()
        assertEquals(filled, sent())
        assertEquals(1, dones)
    }

    @Test
    fun `next stays on a step until its fields are right`() {
        launch()
        next()
        step(R.string.step_what)
        listOf(str(R.string.error_title), str(R.string.error_activity), str(R.string.error_description, OfferDraft.MIN_DESCRIPTION))
            .forEach { compose.onNodeWithText(it).performScrollTo() }
        compose.onNodeWithText(str(R.string.error_species)).assertDoesNotExist()
        type("field-title", "Bear")
        compose.onNodeWithTag("activity-HUNT").performScrollTo().performClick()
        type("field-description", TestGuides.offer.description)
        next()
        step(R.string.step_species)
        next()
        compose.onNodeWithText(str(R.string.error_species)).performScrollTo()
        compose.onNodeWithTag("step-back").performClick()
        step(R.string.step_what)
    }

    @Test
    fun `an offer is hunting or fishing, with the species of that type only`() {
        launch()
        compose.onNodeWithTag("activity-BOTH").assertDoesNotExist()
        compose.onNodeWithText(str(R.string.field_activity_help)).assertExists()
        type("field-title", "Walleye")
        compose.onNodeWithTag("activity-FISH").performScrollTo().performClick()
        type("field-description", TestGuides.offer.description)
        next()
        compose.onNodeWithTag("species-WALLEYE").performScrollTo().performClick()
        compose.onNodeWithTag("species-ELK").assertDoesNotExist()
    }

    @Test
    fun `an older offer for both has to choose a type`() {
        account.give(uid, GuideOffer("old", TestGuides.offer.copy(activity = GuideActivity.BOTH)))
        launch("old")
        compose.onNodeWithTag("change-SPECIES").performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.field_species_help)).assertExists()
        compose.onNodeWithTag("step-back").performClick() // back to the summary
        compose.onNodeWithTag("send").performClick()
        step(R.string.step_what)
        compose.onNodeWithText(str(R.string.error_activity)).performScrollTo()
        compose.onNodeWithTag("activity-HUNT").performScrollTo().performClick()
        next()
        step(R.string.step_review)
    }

    @Test
    fun `when, a duration, other days, half days and the date pickers`() {
        launch()
        fillIn()
        compose.onNodeWithTag("change-WHEN").performScrollTo().performClick()
        step(R.string.step_when)
        compose.onNodeWithTag("field-repeat").assertDoesNotExist()
        compose.onNodeWithTag("length-other").performScrollTo().performClick()
        next()
        compose.onNodeWithText(str(R.string.error_duration, TripLength.MAX_DAYS)).performScrollTo()
        type("field-days", "10")
        compose.onNodeWithTag("length-half").performScrollTo().performClick()
        compose.onNodeWithTag("slot-AFTERNOON").performScrollTo().performClick()
        compose.onNodeWithTag("slot-MORNING").performScrollTo().performClick()
        compose.onNodeWithTag("slot-AFTERNOON").performScrollTo().performClick() // the last one stays
        compose.onNodeWithTag("add-date").performScrollTo().performClick()
        compose.onNodeWithTag("date-picker").assertExists()
        compose.onNodeWithText(app.getString(ca.kallistesoft.guidehunter.core.ui.R.string.dialog_cancel)).performClick()
        compose.onNodeWithTag("add-weekly").performScrollTo().performClick()
        compose.onNodeWithTag("date-range-picker").assertExists()
        compose.onNodeWithText(app.getString(ca.kallistesoft.guidehunter.core.ui.R.string.dialog_cancel)).performClick()
        next()
        step(R.string.step_review)
        compose.onNodeWithText(app.getString(ca.kallistesoft.guidehunter.core.ui.R.string.trip_half_day)).performScrollTo()
    }

    @Test
    fun `an offer on the map opens on its summary, its dates can be changed, and it can be deleted`() {
        account.give(uid, GuideOffer("live", TestGuides.offer, OfferStatus.PUBLISHED, live = true))
        requests.give(TestGuides.request.copy(id = "live_u2", offerId = "live", guideUid = uid, userUid = "u2"))
        launch("live")
        step(R.string.step_review)
        compose.onNodeWithText(str(R.string.offer_live_intro)).assertExists()
        compose.onNodeWithText(TestGuides.offer.title).performScrollTo()
        compose.onNodeWithTag("step-back").assertDoesNotExist()

        compose.onNodeWithTag("change-WHEN").performScrollTo().performClick()
        compose.onNodeWithTag("departure-2027-05-01").performScrollTo()
        compose.onNodeWithTag("these-dates").performScrollTo()
        compose.onNodeWithTag("every-year").performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.repeat_every_year_help)).performScrollTo()
        compose.onNodeWithContentDescription(str(R.string.remove_date, app.departureDate(TestGuides.offer.schedule.departures.single())))
            .performScrollTo().performClick()
        compose.onNodeWithTag("departure-2027-05-01").assertDoesNotExist()
        compose.onNodeWithTag("every-year").assertDoesNotExist()
        next()
        step(R.string.step_review)

        compose.onNodeWithTag("delete-offer").performClick()
        compose.onNodeWithText(str(R.string.offer_delete_live_body)).assertExists()
        compose.onNodeWithText(app.resources.getQuantityString(R.plurals.offer_delete_requests, 1, 1)).assertExists()
        compose.onNodeWithTag("delete-cancel").performClick()
        compose.onNodeWithTag("delete-confirm").assertDoesNotExist()
        assertEquals(0, dones)

        compose.onNodeWithContentDescription(str(R.string.offer_delete)).performClick()
        compose.onNodeWithTag("delete-confirm").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<GuideOffer>(), account.offersByUser.value[uid])
        assertEquals(1, dones)
    }

    @Test
    fun `an offer waiting for review says deleting it is final`() {
        account.give(uid, GuideOffer("pending", TestGuides.offer))
        launch("pending")
        compose.onNodeWithTag("delete-offer").performClick()
        compose.onNodeWithText(str(R.string.offer_delete_body)).assertExists()
        compose.onNodeWithTag("delete-requests").assertDoesNotExist()
    }

    @Test
    fun `without a profile, sending leads to it`() {
        account.profiles.value = emptyMap()
        launch()
        fillIn()
        compose.onNodeWithTag("send").performClick()
        compose.onNodeWithText(str(R.string.offer_needs_profile)).performScrollTo()
        compose.onNodeWithTag("needs-profile").performScrollTo().performClick()
        assertEquals(1, profileOpened)
    }

    @Test
    fun `a wrong price is flagged`() {
        launch()
        fillIn()
        compose.onNodeWithTag("field-price").performTextClearance()
        type("field-price", "0")
        compose.onNodeWithTag("send").performClick()
        compose.onNodeWithText(str(R.string.error_price)).performScrollTo()
        compose.onNodeWithText(str(R.string.form_has_errors)).performScrollTo()
    }

    @Test
    fun `a failed send says so`() {
        account.failure = IllegalStateException()
        launch()
        fillIn()
        compose.onNodeWithTag("send").performClick()
        compose.onNodeWithText(str(R.string.send_failed)).performScrollTo()
    }

    @Test
    fun `a build without a backend says so`() {
        account.failure = BackendNotConfiguredException()
        launch()
        fillIn()
        compose.onNodeWithTag("send").performClick()
        compose.onNodeWithText(str(R.string.send_not_configured)).performScrollTo()
    }

    @Test
    fun `the button says it's sending`() {
        account.onWrite = CompletableDeferred()
        launch()
        fillIn()
        compose.onNodeWithTag("send").performClick()
        compose.onNodeWithText(str(R.string.sending)).assertExists()
    }

    /** Steps 1 and 2 filled in: on the Where step. */
    private fun toWhere() {
        type("field-title", "Bear")
        compose.onNodeWithTag("activity-HUNT").performScrollTo().performClick()
        type("field-description", TestGuides.offer.description)
        next()
        compose.onNodeWithTag("species-BLACK_BEAR").performScrollTo().performClick()
        next()
    }

    @Test
    fun `use my location asks for the permission, then places the pin, or says why not`() {
        launch()
        toWhere()
        compose.onNodeWithTag("use-my-location").performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.location_denied)).performScrollTo()
        assertEquals(0, location.reads)

        permissionAnswer = true
        compose.onNodeWithTag("use-my-location").performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.location_failed)).performScrollTo() // the fake has no fix yet
        assertEquals(1, location.reads)

        location.location = PICKED
        compose.onNodeWithTag("use-my-location").performScrollTo().performClick() // allowed now: no dialog
        compose.onNodeWithText(str(R.string.field_location_set)).performScrollTo()
        compose.onNodeWithText(str(R.string.location_failed)).assertDoesNotExist()
        assertEquals(2, location.reads)
    }

    @Test
    fun `typed coordinates place the pin, once they're within the world`() {
        launch()
        fillIn() // the map tap places PICKED; the coordinates below replace it
        compose.onNodeWithTag("change-WHERE").performScrollTo().performClick()
        compose.onNodeWithTag("type-coordinates").performScrollTo().performClick()
        // The fields start with the pin's coordinates (48.0, -79.0): valid, so "Place the pin" is enabled.
        compose.onNodeWithTag("latitude").performScrollTo().performTextClearance()
        compose.onNodeWithTag("latitude").performTextInput("48,24")
        compose.onNodeWithTag("longitude").performScrollTo().performTextClearance()
        compose.onNodeWithTag("longitude").performTextInput("-200")
        compose.onNodeWithTag("coordinates-ok").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("longitude").performScrollTo().performTextClearance()
        compose.onNodeWithTag("longitude").performTextInput("-79.02")
        compose.onNodeWithTag("coordinates-ok").performScrollTo().performClick()
        compose.onNodeWithTag("coordinates").assertDoesNotExist()
        compose.onNodeWithText(str(R.string.field_location_set)).performScrollTo()

        compose.onNodeWithTag("type-coordinates").performScrollTo().performClick()
        compose.onNodeWithTag("coordinates-cancel").performScrollTo().performClick()
        compose.onNodeWithTag("latitude").assertDoesNotExist()
        next() // back to the summary
        compose.onNodeWithTag("send").performClick()
        compose.waitForIdle()
        assertEquals(GeoPoint(48.24, -79.02), sent().location)
    }

    @Test
    fun `back goes a step back, and leaves from the first`() {
        launch()
        toWhere()
        compose.onNodeWithTag("back").performClick()
        step(R.string.step_species)
        compose.onNodeWithTag("step-back").performClick()
        step(R.string.step_what)
        assertEquals(0, backs)
        compose.onNodeWithTag("back").performClick()
        assertEquals(1, backs)
    }

    private companion object {
        val PICKED = GeoPoint(48.0, -79.0)
    }
}
