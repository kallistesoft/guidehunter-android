package ca.kallistesoft.guidehunter

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * The real app (real Hilt graph, no Firebase in tests: the sample guides) under Robolectric: the map opens with its
 * guides, a guide's contact unlocks (ads are off under tests), and the guide's space opens and closes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp")
class GuideHunterAppTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `the map shows the guides, and a guide's contact unlocks`() {
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasTag("guide-sample-bear")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("guide-sample-bear").performClick()
        compose.onNodeWithTag("unlock").performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasTag("contact-name")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `become a guide opens the sign-in, and back returns to the map`() {
        compose.onNodeWithTag("become-guide").performClick()
        compose.onNodeWithTag("sign-in").assertExists()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("become-guide").assertExists()
        // Back on the map (the only entry) leaves the app, as Android does.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.activityRule.scenario.onActivity { assertTrue(it.isFinishing) }
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

    private fun hasTag(tag: String) = androidx.compose.ui.test.hasTestTag(tag)

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
