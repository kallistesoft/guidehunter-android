package ca.kallistesoft.guidehunter.feature.map

import android.Manifest
import android.app.Application
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
import ca.kallistesoft.guidehunter.core.model.AppSettings
import ca.kallistesoft.guidehunter.core.model.Discipline
import ca.kallistesoft.guidehunter.core.model.GeoBounds
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.ui.R as UiR
import ca.kallistesoft.guidehunter.core.auth.AccountSession
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGoogleSignIn
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGuideRepository
import ca.kallistesoft.guidehunter.core.testing.FakeRequestRepository
import ca.kallistesoft.guidehunter.core.testing.FakeUserAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeLocationRepository
import ca.kallistesoft.guidehunter.core.testing.FakeRewardedAds
import ca.kallistesoft.guidehunter.core.testing.FakeSettingsRepository
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
import kotlin.time.Duration.Companion.days

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
    private var settings = 0

    /** What the system answers to the permission request: granted or not. */
    private val appSettings = FakeSettingsRepository()
    private val speciesFilters = mutableListOf<Discipline?>()
    private var permissionAnswer = false
    private var permissionAsked = 0
    private val auth = FakeAuthRepository()
    private val google = FakeGoogleSignIn()
    private val users = FakeUserAccountRepository()
    private val requests = FakeRequestRepository()
    private var myContact = 0
    private var myRequests = 0

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
        val session = AccountSession(auth, google, FakeGuideAccountRepository(requests), users, requests, appSettings)
        val viewModel = MapViewModel(guides, location, ads, appSettings, session, users, requests, SavedStateHandle(), { TestGuides.today }, { TestGuides.now })
        compose.setContent {
            val owner = object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                GuideHunterTheme {
                    MapScreen(
                        onSettings = { settings++ },
                        onSpeciesFilter = { speciesFilters += it },
                        onMyContact = { myContact++ },
                        onMyRequests = { myRequests++ },
                        viewModel = viewModel,
                    ) { content, modifier ->
                        // A pin per guide, tapped like on the map.
                        Column(modifier) {
                            LazyRow {
                                items(content.guides.size) { index ->
                                    val id = content.guides[index].guide.id
                                    val star = if (id in content.featured) " featured" else ""
                                    Text("pin $id$star", Modifier.testTag("pin-$id").clickable { content.onSelect(id) })
                                }
                            }
                            // The camera stopping over an area, like a pan or a zoom.
                            Text("pan west", Modifier.testTag("pan-west").clickable { content.onVisibleArea(WEST_OF_GASPE) })
                            Text("pan north", Modifier.testTag("pan-north").clickable { content.onVisibleArea(FAR_NORTH) })
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `a boosted guide's pin is featured, its card first and labelled, its sheet badged`() {
        guides.guides.value = TestGuides.all + TestGuides.moose.copy(id = "boosted", title = "Boosted trip", boostedUntil = TestGuides.now + 1.days)
        launch()
        compose.onNodeWithText("pin boosted featured").assertExists()
        compose.onNodeWithText("pin bear").assertExists()
        compose.onNodeWithTag("featured-boosted", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("featured-bear", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("guide-boosted").performClick()
        compose.onNode(hasTestTag("featured-boosted") and hasAnyAncestor(hasTestTag("guide-detail")), useUnmergedTree = true)
            .assertExists()
        assertEquals(str(UiR.string.featured), "Featured")
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
        compose.onNodeWithText(count(2)).assertIsDisplayed()
        compose.onNodeWithTag("discipline-all").performClick()
        compose.onNodeWithText(count(3)).assertIsDisplayed()
    }

    @Test
    fun `the species chip opens the species screen with the chosen discipline`() {
        launch()
        compose.onNodeWithTag("species-picker").performClick()
        compose.onNodeWithTag("discipline-FISHING").performClick()
        compose.onNodeWithTag("species-picker").performClick()
        assertEquals(listOf(null, Discipline.FISHING), speciesFilters)
    }

    @Test
    fun `the species chosen in the settings narrow the map and name the chip`() {
        launch()
        compose.onNodeWithText(str(R.string.filter_species)).assertExists()
        appSettings.settings.value = AppSettings(speciesFilter = setOf(Species.WALLEYE))
        compose.waitForIdle()
        compose.onNodeWithText(count(1)).assertIsDisplayed()
        compose.onNodeWithText(str(UiR.string.species_walleye)).assertExists()
        // Several species: the guides offering any of them.
        appSettings.settings.value = AppSettings(speciesFilter = setOf(Species.WALLEYE, Species.BLACK_BEAR))
        compose.waitForIdle()
        compose.onNodeWithText(count(2)).assertIsDisplayed()
        compose.onNodeWithText(app.resources.getQuantityString(R.plurals.filter_species_count, 2, 2)).assertExists()
    }

    @Test
    fun `the dates chip opens the dates picker, which clears them`() {
        launch()
        compose.onNodeWithTag("dates-picker").performClick()
        compose.onNodeWithTag("date-range-picker").assertExists()
        compose.onNodeWithTag("dates-clear").performClick()
        compose.onNodeWithTag("date-range-picker").assertDoesNotExist()
        compose.onNodeWithText(count(3)).assertIsDisplayed()
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
    fun `a card opens the offer, after the ad, interested signs in, confirms and sends the request`() {
        launch()
        compose.onNodeWithTag("guide-bear").performClick()
        compose.onNodeWithTag("guide-detail").assertIsDisplayed()
        compose.onNode(hasText(TestGuides.bear.title) and hasAnyAncestor(hasTestTag("guide-detail"))).assertExists()
        compose.onNodeWithText(str(R.string.contact_locked)).assertExists()
        compose.onNodeWithTag("interested").assertDoesNotExist()

        compose.onNodeWithTag("unlock").performScrollTo().performClick()
        compose.onNodeWithTag("interested").performScrollTo().performClick()
        assertEquals("signed in with Google", 1, google.shown)
        compose.onNodeWithTag("confirm-request").assertExists()
        compose.onNodeWithText(TestGuides.user.email).assertExists()
        compose.onNodeWithTag("edit-contact").performScrollTo().performClick()
        assertEquals(1, myContact)
        compose.onNodeWithTag("send-request").performScrollTo().performClick()
        compose.onNodeWithTag("confirm-request").assertDoesNotExist()

        assertEquals(RequestStatus.PENDING, requests.requests.value.getValue("bear_${TestGuides.user.uid}").status)
        compose.onNodeWithText(str(R.string.request_sent)).assertExists()
        compose.onNode(hasText(str(UiR.string.my_requests)) and hasAnyAncestor(hasTestTag("request-sent"))).performClick()
        compose.waitForIdle()
        assertEquals(1, myRequests)
        compose.onNodeWithTag("status-PENDING").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `not now closes the confirmation, and a sent request can be cancelled then sent again`() {
        auth.user.value = TestGuides.user
        requests.give(TestGuides.request)
        launch()
        compose.onNodeWithTag("guide-bear").performClick()
        compose.onNodeWithTag("unlock").assertDoesNotExist()
        compose.onNodeWithTag("my-requests").performScrollTo().performClick()
        assertEquals(1, myRequests)
        compose.onNodeWithTag("cancel-request").performScrollTo().performClick()
        compose.onNodeWithTag("status-CANCELLED").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("send-again").performScrollTo().performClick()
        compose.onNodeWithTag("not-now").performScrollTo().performClick()
        compose.onNodeWithTag("confirm-request").assertDoesNotExist()
        compose.onNodeWithTag("send-again").performScrollTo().performClick()
        compose.onNodeWithTag("send-request").performScrollTo().performClick()
        compose.onNodeWithTag("status-PENDING").performScrollTo().assertIsDisplayed()
        assertEquals(0, ads.shown)
    }

    @Test
    fun `an incomplete contact opens the contact form first`() {
        auth.user.value = TestGuides.user.copy(name = "")
        launch()
        compose.onNodeWithTag("guide-bear").performClick()
        compose.onNodeWithTag("unlock").performScrollTo().performClick()
        compose.onNodeWithTag("interested").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, myContact)
        compose.onNodeWithTag("confirm-request").assertDoesNotExist()
    }

    @Test
    fun `the account's own offer offers guide mode instead`() {
        auth.user.value = TestGuides.user.copy(uid = TestGuides.OWNER)
        launch()
        compose.onNodeWithTag("guide-bear").performClick()
        compose.onNodeWithText(str(R.string.own_offer)).performScrollTo()
        compose.onNodeWithTag("own-switch").performScrollTo().performClick()
        assertEquals(1, settings)
    }

    @Test
    fun `a pin opens the offer too`() {
        launch()
        compose.onNodeWithTag("pin-salmon").performClick()
        compose.onNodeWithTag("guide-detail").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.price_on_request)).assertExists()
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
        compose.onNodeWithTag("interested").performScrollTo().assertIsDisplayed()
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
    fun `the settings button opens the settings`() {
        launch()
        compose.onNodeWithTag("settings").performClick()
        assertEquals(1, settings)
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
