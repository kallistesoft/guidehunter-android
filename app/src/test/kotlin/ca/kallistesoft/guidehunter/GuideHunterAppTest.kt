package ca.kallistesoft.guidehunter

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ca.kallistesoft.guidehunter.di.AppModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The real app (real Hilt graph, no Firebase in tests: the sample guides; the real settings DataStore, fresh per test)
 * under Robolectric: a first launch opens the welcome, "Later" leads to the map with its guides, the ad before
 * "I'm interested" (off under tests), and the settings open and close.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp")
class GuideHunterAppTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `later on the welcome opens the map, and after the ad a guide can be told I'm interested`() {
        later()
        waitFor("guide-sample-bear")
        compose.onNodeWithTag("guide-sample-bear").performClick()
        compose.onNodeWithTag("unlock").performScrollTo().performClick()
        waitFor("interested")
    }

    @Test
    fun `the settings open from the map, back returns to it, and back on the map leaves the app`() {
        later()
        compose.onNodeWithTag("settings").performClick()
        waitFor("settings-screen")
        compose.onNodeWithTag("back").performClick()
        waitFor("locate")
        compose.onNodeWithTag("settings-screen").assertDoesNotExist()
        // Back on the map (the only entry) leaves the app, as Android does.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.activityRule.scenario.onActivity { assertTrue(it.isFinishing) }
    }

    @Test
    fun `the species chip opens the species screen, the choice filters the map and is kept`() {
        later()
        compose.onNodeWithTag("discipline-FISHING").performClick()
        compose.onNodeWithTag("species-picker").performClick()
        waitFor("species-filter-screen")
        // From the map, only the discipline chosen there.
        compose.onNodeWithTag("species-BLACK_BEAR").assertDoesNotExist()
        compose.onNodeWithTag("species-ATLANTIC_SALMON").performScrollTo().performClick()
        compose.onNodeWithTag("species-done").performClick()
        waitFor("locate")
        waitFor("species-picker", "Atlantic salmon")
        waitFor("guide-sample-salmon")
        compose.onNodeWithTag("guide-sample-moose").assertDoesNotExist()

        compose.activityRule.scenario.recreate()
        waitFor("guide-sample-salmon")
        waitFor("species-picker", "Atlantic salmon")
        compose.onNodeWithTag("guide-sample-moose").assertDoesNotExist()

        // Also in the settings, which list every species.
        compose.onNodeWithTag("settings").performClick()
        waitFor("species-filter", "Atlantic salmon")
        compose.onNodeWithTag("species-filter").performClick()
        waitFor("species-filter-screen")
        compose.onNodeWithTag("species-group-HUNTING").assertExists()
        compose.onNodeWithTag("species-any").performClick()
        compose.onNodeWithTag("back").performClick()
        waitFor("species-filter", "Any species")
    }

    @Test
    fun `ads are off under tests, offers go to the configured address, and sign-in uses Firebase's web client`() {
        assertFalse(AppModule.adsConfig().enabled)
        assertEquals(BuildConfig.APPLICATIONS_EMAIL, AppModule.dataConfig().applicationsEmail)
        // Generated from google-services.json when the build has one (local, CI); none in a fresh clone.
        compose.activityRule.scenario.onActivity {
            val id = AppModule.googleSignInConfig(it).webClientId
            assertTrue("$id", id == null || id.endsWith(".apps.googleusercontent.com"))
        }
    }

    /** A first launch: the welcome, then "Later" to the map. */
    private fun later() {
        waitFor("welcome")
        compose.onNodeWithTag("later").performClick()
        waitFor("locate")
    }

    private fun waitFor(tag: String) =
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    /** [tag] showing [text] (the settings DataStore writes in the background). */
    private fun waitFor(tag: String, text: String) =
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
