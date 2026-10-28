package ca.kallistesoft.guidehunter.feature.map

import android.Manifest
import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.model.GeoBounds
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.testing.FakeAdConsent
import ca.kallistesoft.guidehunter.core.testing.FakeGuideRepository
import ca.kallistesoft.guidehunter.core.testing.FakeLocationRepository
import ca.kallistesoft.guidehunter.core.testing.FakeRewardedAds
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

/** The map screen with a stand-in map that lists the pins it was given; Google Maps itself can't draw on the JVM. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp")
class MapScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val guides = FakeGuideRepository()
    private val location = FakeLocationRepository()
    private val ads = FakeRewardedAds()
    private val consent = FakeAdConsent()
    private var becomeGuide = 0

    /** What the system answers to the permission request: granted or not. */
    private var permissionAnswer = false
    private var permissionAsked = 0

    private fun str(id: Int, vararg args: Any) = app.getString(id, *args)
    private fun count(n: Int) = app.resources.getQuantityString(R.plurals.guide_count, n, n)

    /** Answers a permission request at once with [permissionAnswer], like the system dialog would. */
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: androidx.core.app.ActivityOptionsCompat?) {
            permissionAsked++
            if (permissionAnswer) shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
            dispatchResult(requestCode, permissionAnswer as O)
        }
    }

    private fun launch() {
        val viewModel = MapViewModel(guides, location, ads, consent, SavedStateHandle())
        compose.setContent {
            val owner = object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                GuideHunterTheme {
                    MapScreen(onBecomeGuide = { becomeGuide++ }, viewModel = viewModel, map = { content, modifier ->
                        // A pin per guide, tapped like on the map.
                        Column(modifier) {
                            LazyRow {
                                items(content.guides.size) { index ->
                                    val id = content.guides[index].guide.id
                                    Text("pin $id", Modifier.testTag("pin-$id").clickable { content.onSelect(id) })
                                }
                            }
                            // The camera stopping over an area, like a pan or a zoom.
                            Text("pan west", Modifier.testTag("pan-west").clickable { content.onVisibleArea(WEST_OF_GASPE) })
                            Text("pan north", Modifier.testTag("pan-north").clickable { content.onVisibleArea(FAR_NORTH) })
                        }
                    })
                }
            }
        }
    }

    @Test
    fun `every guide gets a pin and a card`() {
        launch()
        compose.onNodeWithTag("guide-count").assertIsDisplayed()
        compose.onNodeWithText(count(3)).assertIsDisplayed()
        listOf("bear", "salmon", "moose").forEach { compose.onNodeWithTag("pin-$it").assertExists() }
        compose.onNodeWithTag("guide-bear").assertIsDisplayed()
    }

    @Test
    fun `the filters narrow the guides`() {
        launch()
        compose.onNodeWithTag("discipline-FISHING").performClick()
        compose.onNodeWithTag("pin-bear").assertDoesNotExist()
        compose.onNodeWithTag("species-picker").performClick()
        compose.onNodeWithTag("species-WALLEYE").performClick()
        compose.onNodeWithText(count(1)).assertIsDisplayed()
        compose.onNodeWithTag("species-picker").performClick()
        compose.onNodeWithTag("species-any").performClick()
        compose.onNodeWithTag("discipline-all").performClick()
        compose.onNodeWithText(count(3)).assertIsDisplayed()
    }

    @Test
    fun `with all disciplines, each species list opens under its heading`() {
        launch()
        compose.onNodeWithTag("species-picker").performClick()
        compose.onNodeWithTag("species-WALLEYE").assertDoesNotExist()
        compose.onNodeWithTag("species-group-FISHING").performClick()
        compose.onNodeWithTag("species-WALLEYE").assertExists()
        compose.onNodeWithTag("species-BLACK_BEAR").assertDoesNotExist()
        compose.onNodeWithTag("species-group-FISHING").performClick()
        compose.onNodeWithTag("species-WALLEYE").assertDoesNotExist()
        compose.onNodeWithTag("species-group-FISHING").performClick()
        compose.onNodeWithTag("species-WALLEYE").performClick()
        compose.onNodeWithText(count(1)).assertIsDisplayed()
        // The chosen species' list is open the next time.
        compose.onNodeWithTag("species-picker").performClick()
        compose.onNodeWithTag("species-WALLEYE").assertExists()
    }

    @Test
    fun `the cards follow the part of the map on screen`() {
        launch()
        compose.onNodeWithTag("pan-west").performClick()
        compose.onNodeWithText(count(2)).assertIsDisplayed()
        compose.onNodeWithTag("guide-salmon").assertDoesNotExist()
        compose.onNodeWithTag("pin-salmon").assertExists()
        compose.onNodeWithTag("pan-north").performClick()
        compose.onNodeWithText(str(R.string.no_guides_here)).assertIsDisplayed()
    }

    @Test
    fun `no guide matching says so`() {
        guides.guides.value = emptyList()
        launch()
        compose.onNodeWithText(str(R.string.no_guides)).assertIsDisplayed()
    }

    @Test
    fun `a card opens the offer, and an ad shows the contact, whose links open`() {
        launch()
        compose.onNodeWithTag("guide-bear").performClick()
        compose.onNodeWithTag("guide-detail").assertIsDisplayed()
        compose.onNode(hasText(TestGuides.bear.title) and hasAnyAncestor(hasTestTag("guide-detail"))).assertExists()
        compose.onNodeWithText(str(R.string.contact_hidden)).assertExists()
        compose.onNodeWithTag("contact-name").assertDoesNotExist()

        compose.onNodeWithTag("unlock").performScrollTo().performClick()
        compose.onNodeWithTag("contact-name").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(TestGuides.bearContact.name).assertExists()

        compose.onNodeWithTag("contact-phone").performScrollTo().performClick()
        assertEquals(Intent.ACTION_DIAL, shadowOf(app).nextStartedActivity.action)
        compose.onNodeWithTag("contact-email").performScrollTo().performClick()
        assertEquals(Intent.ACTION_SENDTO, shadowOf(app).nextStartedActivity.action)
        compose.onNodeWithTag("contact-website").performScrollTo().performClick()
        assertEquals("https://example.com", shadowOf(app).nextStartedActivity.data.toString())
    }

    @Test
    fun `a pin opens the offer too`() {
        launch()
        compose.onNodeWithTag("pin-salmon").performClick()
        compose.onNodeWithTag("guide-detail").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.price_on_request)).assertExists()
    }

    @Test
    fun `privacy options show only where required, and open the consent choices`() {
        launch()
        compose.onNodeWithTag("privacy-options").assertDoesNotExist()
        consent.privacyOptionsRequired.value = true
        compose.onNodeWithTag("privacy-options").performClick()
        assertEquals(1, consent.privacyOptionsShown)
    }

    @Test
    @Config(qualifiers = "en-w1000dp-h800dp")
    fun `on a wide window the offer opens beside the map, and closes`() {
        launch()
        compose.onNodeWithTag("guide-bear").performClick()
        compose.onNodeWithTag("guide-pane").assertIsDisplayed()
        compose.onNodeWithTag("guide-detail").assertIsDisplayed()
        compose.onNodeWithTag("guide-count").assertIsDisplayed() // the map stays in view beside it
        compose.onNodeWithTag("unlock").performScrollTo().performClick()
        compose.onNodeWithTag("contact-name").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("pane-close").performClick()
        compose.onNodeWithTag("guide-pane").assertDoesNotExist()
    }

    @Test
    fun `on a phone the offer opens as a sheet, not a pane`() {
        launch()
        compose.onNodeWithTag("guide-bear").performClick()
        compose.onNodeWithTag("guide-detail").assertIsDisplayed()
        compose.onNodeWithTag("guide-pane").assertDoesNotExist()
    }

    @Test
    fun `become a guide opens the form`() {
        launch()
        compose.onNodeWithTag("become-guide").performClick()
        assertEquals(1, becomeGuide)
    }

    private val distance
        get() = "Abitibi · " + str(R.string.distance_km, TestGuides.bear.location.distanceKm(TestGuides.quebecCity).roundToInt().toString())

    @Test
    fun `the location button asks for the permission, then reads the location`() {
        launch()
        assertEquals("read once at start: nothing without the permission", 1, location.reads)
        compose.onNodeWithText(distance).assertDoesNotExist()

        location.location = TestGuides.quebecCity
        permissionAnswer = true
        compose.onNodeWithTag("locate").performClick()
        assertEquals(1, permissionAsked)
        assertEquals(2, location.reads)
        compose.onNodeWithText(distance).assertIsDisplayed()

        compose.onNodeWithTag("locate").performClick() // allowed now: no dialog, read again
        assertEquals(1, permissionAsked)
        assertEquals(3, location.reads)
    }

    @Test
    fun `with the permission given before, distances show at once`() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        location.location = TestGuides.quebecCity
        launch()
        assertEquals(1, location.reads)
        // Nearest first: the first card is La Tuque's, the bear's is off screen in the row.
        val nearest = "La Tuque · " + str(R.string.distance_km, TestGuides.moose.location.distanceKm(TestGuides.quebecCity).roundToInt().toString())
        compose.onNodeWithText(nearest).assertIsDisplayed()
    }

    @Test
    fun `denied once, the app explains and asks again on Allow`() {
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION, true)
        launch()
        compose.onNodeWithTag("locate").performClick()
        compose.onNodeWithText(str(R.string.location_rationale)).assertIsDisplayed()
        compose.onNodeWithTag("rationale-not-now").performClick()
        compose.onNodeWithText(str(R.string.location_rationale)).assertDoesNotExist()

        compose.onNodeWithTag("locate").performClick() // asks again (2), still denied: the dialog again
        compose.onNodeWithText(str(R.string.location_rationale)).assertIsDisplayed()
        compose.onNodeWithTag("rationale-allow").performClick()
        assertEquals("asked again from the dialog", 3, permissionAsked)
    }

    @Test
    fun `denied for good, the app opens its settings page`() {
        launch()
        compose.onNodeWithTag("locate").performClick()
        val settings = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settings.action)
        assertEquals("package:" + app.packageName, settings.data.toString())
        assertEquals(1, location.reads)
    }
}

/** Southern Quebec, west of Gaspé: the bear and the moose. */
private val WEST_OF_GASPE = GeoBounds(GeoPoint(45.0, -80.0), GeoPoint(50.0, -70.0))

/** Nunavik: no test guide. */
private val FAR_NORTH = GeoBounds(GeoPoint(55.0, -80.0), GeoPoint(62.0, -60.0))
