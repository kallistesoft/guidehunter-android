package ca.kallistesoft.guidehunter.feature.becomeguide

import android.Manifest
import android.app.Application
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
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
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.GuideApplication
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.testing.FakeGuideApplicationRepository
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

/** The form with a stand-in map picker (a tap places the pin at [PICKED]); Google Maps can't draw on the JVM. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp")
class BecomeGuideScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val repository = FakeGuideApplicationRepository()
    private val location = FakeLocationRepository()
    private var backs = 0

    /** What the system answers to the location permission request. */
    private var permissionAnswer = false

    private fun str(id: Int, vararg args: Any) = app.getString(id, *args)

    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: androidx.core.app.ActivityOptionsCompat?) {
            if (permissionAnswer) shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
            dispatchResult(requestCode, permissionAnswer as O)
        }
    }

    private fun launch() {
        val viewModel = BecomeGuideViewModel(repository, location, SavedStateHandle())
        compose.setContent {
            val owner = object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                GuideHunterTheme {
                    BecomeGuideScreen(onBack = { backs++ }, viewModel = viewModel, picker = { _, onPick, modifier ->
                        Box(modifier.clickable { onPick(PICKED) })
                    })
                }
            }
        }
    }

    private fun type(tag: String, text: String) = compose.onNodeWithTag(tag).performScrollTo().performTextInput(text)

    private fun fillIn() {
        type("field-business-name", "Pourvoirie Test")
        compose.onNodeWithTag("activity-HUNT").performScrollTo().performClick()
        compose.onNodeWithTag("species-BLACK_BEAR").performScrollTo().performClick()
        type("field-description", TestGuides.application.description)
        type("field-region", "Abitibi")
        compose.onNodeWithTag("location-picker").performScrollTo().performClick()
        type("field-price", "2400")
        type("field-email", "guide@example.com")
        type("field-phone", "819 555 0100")
        type("field-website", "example.com")
    }

    @Test
    fun `a filled-in form is sent, then thanks the guide`() {
        launch()
        compose.onNodeWithText(str(R.string.field_species_help)).performScrollTo()
        fillIn()
        compose.onNodeWithText(str(R.string.field_location_set)).performScrollTo()
        compose.onNodeWithTag("send").performScrollTo().performClick()
        compose.onNodeWithTag("sent").assertExists()
        assertEquals(
            TestGuides.application.copy(location = PICKED, website = "example.com", species = setOf(Species.BLACK_BEAR), activity = GuideActivity.HUNT),
            repository.submitted.single(),
        )
        compose.onNodeWithTag("back-to-map").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `an empty form shows every error`() {
        launch()
        compose.onNodeWithTag("send").performScrollTo().performClick()
        listOf(
            str(R.string.error_business_name),
            str(R.string.error_activity),
            str(R.string.error_species),
            str(R.string.error_description, GuideApplication.MIN_DESCRIPTION),
            str(R.string.error_region),
            str(R.string.error_location),
            str(R.string.error_email),
            str(R.string.form_has_errors),
        ).forEach { compose.onNodeWithText(it).performScrollTo() }
    }

    @Test
    fun `a wrong price and phone are flagged`() {
        launch()
        type("field-price", "0")
        type("field-phone", "555")
        compose.onNodeWithTag("send").performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.error_price)).performScrollTo()
        compose.onNodeWithText(str(R.string.error_phone)).performScrollTo()
    }

    @Test
    fun `a failed send says so`() {
        repository.failure = IllegalStateException()
        launch()
        fillIn()
        compose.onNodeWithTag("send").performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.send_failed)).performScrollTo()
    }

    @Test
    fun `a build without a backend says so`() {
        repository.failure = BackendNotConfiguredException()
        launch()
        fillIn()
        compose.onNodeWithTag("send").performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.send_not_configured)).performScrollTo()
    }

    @Test
    fun `the button says it's sending`() {
        repository.onSubmit = CompletableDeferred()
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
        assertEquals(GeoPoint(48.24, -79.02), repository.submitted.single().location)
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
