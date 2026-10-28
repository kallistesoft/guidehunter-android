package ca.kallistesoft.guidehunter.feature.becomeguide

import android.Manifest
import android.app.Application
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextClearance
import org.robolectric.Shadows.shadowOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import ca.kallistesoft.guidehunter.core.model.OfferStatus
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeLocationRepository
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
    private val account = FakeGuideAccountRepository().apply { profiles.value = mapOf(uid to TestGuides.profile) }
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
        val viewModel = OfferEditorViewModel(offerId, auth, account, location, SavedStateHandle())
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

    private fun fillIn() {
        type("field-title", TestGuides.offer.title)
        compose.onNodeWithTag("activity-HUNT").performScrollTo().performClick()
        compose.onNodeWithTag("species-BLACK_BEAR").performScrollTo().performClick()
        type("field-description", TestGuides.offer.description)
        type("field-region", "Abitibi")
        compose.onNodeWithTag("location-picker").performScrollTo().performClick()
        type("field-price", "2400")
    }

    private fun sent(): OfferDraft = account.offersByUser.value.getValue(uid).single().draft

    @Test
    fun `offering both, hunting and fishing species open apart and count what's chosen`() {
        launch()
        compose.onNodeWithTag("activity-BOTH").performScrollTo().performClick()
        compose.onNodeWithTag("species-ELK").assertDoesNotExist()
        compose.onNodeWithTag("species-group-HUNTING").performScrollTo().performClick()
        compose.onNodeWithTag("species-ELK").performScrollTo().performClick()
        compose.onNodeWithText(app.resources.getQuantityString(R.plurals.species_chosen, 1, 1)).assertExists()
        compose.onNodeWithTag("species-group-HUNTING").performScrollTo().performClick()
        compose.onNodeWithTag("species-ELK").assertDoesNotExist()
        compose.onNodeWithTag("species-group-FISHING").performScrollTo().performClick()
        compose.onNodeWithTag("species-WALLEYE").assertExists()
    }

    @Test
    fun `a filled-in offer is sent for review, then the screen closes`() {
        launch()
        compose.onNodeWithText(str(R.string.offer_new_title)).assertExists()
        compose.onNodeWithText(str(R.string.offer_intro)).assertExists()
        compose.onNodeWithTag("delete-offer").assertDoesNotExist()
        compose.onNodeWithText(str(R.string.field_species_help)).performScrollTo()
        fillIn()
        compose.onNodeWithText(str(R.string.field_location_set)).performScrollTo()
        compose.onNodeWithTag("send").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(TestGuides.offer.copy(location = PICKED), sent())
        assertEquals(1, dones)
    }

    @Test
    fun `an offer on the map opens with its text, says it stays there, and can be deleted`() {
        account.give(uid, GuideOffer("live", TestGuides.offer, OfferStatus.PUBLISHED, live = true))
        launch("live")
        compose.onNodeWithText(str(R.string.offer_edit_title)).assertExists()
        compose.onNodeWithText(str(R.string.offer_live_intro)).assertExists()
        compose.onNodeWithText(TestGuides.offer.title).assertExists()

        compose.onNodeWithTag("delete-offer").performClick()
        compose.onNodeWithText(str(R.string.offer_delete_live_body)).assertExists()
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
        compose.onNodeWithText(str(R.string.offer_intro)).assertExists()
        compose.onNodeWithTag("delete-offer").performClick()
        compose.onNodeWithText(str(R.string.offer_delete_body)).assertExists()
    }

    @Test
    fun `without a profile, sending leads to it`() {
        account.profiles.value = emptyMap()
        launch()
        fillIn()
        compose.onNodeWithTag("send").performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.offer_needs_profile)).performScrollTo()
        compose.onNodeWithTag("needs-profile").performScrollTo().performClick()
        assertEquals(1, profileOpened)
    }

    @Test
    fun `an empty form shows every error`() {
        launch()
        compose.onNodeWithTag("send").performScrollTo().performClick()
        listOf(
            str(R.string.error_title),
            str(R.string.error_activity),
            str(R.string.error_species),
            str(R.string.error_description, OfferDraft.MIN_DESCRIPTION),
            str(R.string.error_region),
            str(R.string.error_location),
            str(R.string.form_has_errors),
        ).forEach { compose.onNodeWithText(it).performScrollTo() }
    }

    @Test
    fun `a wrong price is flagged`() {
        launch()
        type("field-price", "0")
        compose.onNodeWithTag("send").performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.error_price)).performScrollTo()
    }

    @Test
    fun `a failed send says so`() {
        account.failure = IllegalStateException()
        launch()
        fillIn()
        compose.onNodeWithTag("send").performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.send_failed)).performScrollTo()
    }

    @Test
    fun `a build without a backend says so`() {
        account.failure = BackendNotConfiguredException()
        launch()
        fillIn()
        compose.onNodeWithTag("send").performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.send_not_configured)).performScrollTo()
    }

    @Test
    fun `the button says it's sending`() {
        account.onWrite = CompletableDeferred()
        launch()
        fillIn()
        compose.onNodeWithTag("send").performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.sending)).performScrollTo()
    }

    @Test
    fun `use my location asks for the permission, then places the pin, or says why not`() {
        launch()
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
        compose.onNodeWithTag("send").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(GeoPoint(48.24, -79.02), sent().location)
    }

    @Test
    fun `back leaves the form`() {
        launch()
        compose.onNodeWithTag("back").performClick()
        assertEquals(1, backs)
    }

    private companion object {
        val PICKED = GeoPoint(48.0, -79.0)
    }
}
