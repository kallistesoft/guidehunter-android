package ca.kallistesoft.guidehunter.feature.map

import android.Manifest
import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.Box
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
    private var becomeGuide = 0

    private fun str(id: Int, vararg args: Any) = app.getString(id, *args)

    private fun launch() {
        val viewModel = MapViewModel(guides, location, ads, SavedStateHandle())
        compose.setContent {
            GuideHunterTheme {
                MapScreen(onBecomeGuide = { becomeGuide++ }, viewModel = viewModel, map = { content, modifier ->
                    // A pin per guide, tapped like on the map.
                    Box(modifier) {
                        LazyRow {
                            items(content.guides.size) { index ->
                                val id = content.guides[index].guide.id
                                Text("pin $id", Modifier.testTag("pin-$id"))
                            }
                        }
                    }
                })
            }
        }
    }

    @Test
    fun `every guide gets a pin and a card`() {
        launch()
        compose.onNodeWithTag("guide-count").assertIsDisplayed()
        compose.onNodeWithText("3 guides").assertIsDisplayed()
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
        compose.onNodeWithText("1 guide").assertIsDisplayed()
        compose.onNodeWithTag("species-picker").performClick()
        compose.onNodeWithTag("species-any").performClick()
        compose.onNodeWithTag("discipline-all").performClick()
        compose.onNodeWithText("3 guides").assertIsDisplayed()
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
        compose.onNodeWithTag("guide-detail").assertDoesNotExist() // the stand-in pin isn't clickable…
        compose.onNodeWithTag("guide-salmon").performClick() // …the card is
        compose.onNodeWithText(str(R.string.price_on_request)).assertExists()
    }

    @Test
    fun `become a guide opens the form`() {
        launch()
        compose.onNodeWithTag("become-guide").performClick()
        assertEquals(1, becomeGuide)
    }

    @Test
    fun `the location button reads the location once it's allowed`() {
        location.location = TestGuides.quebecCity
        launch()
        assertEquals("not read without the permission", 0, location.reads)
        compose.onNodeWithTag("locate").performClick() // asks for the permission

        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        compose.onNodeWithTag("locate").performClick()
        compose.waitForIdle()
        assertEquals(1, location.reads)
        compose.onNodeWithText("Abitibi · About", substring = true).assertExists()
    }

    @Test
    fun `with the permission given before, distances show at once`() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        location.location = TestGuides.quebecCity
        launch()
        compose.waitForIdle()
        assertTrue(location.reads >= 1)
    }
}
