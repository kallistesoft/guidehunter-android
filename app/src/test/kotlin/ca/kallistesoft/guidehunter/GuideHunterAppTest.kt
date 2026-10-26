package ca.kallistesoft.guidehunter

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import ca.kallistesoft.guidehunter.di.AppModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The real app (real Hilt graph, no Firebase in tests: the sample guides) under Robolectric: the map opens with its
 * guides, a guide's contact unlocks (ads are off under tests), and the form opens and closes.
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
    fun `become a guide opens the form, and back returns to the map`() {
        compose.onNodeWithTag("become-guide").performClick()
        compose.onNodeWithTag("field-business-name").assertExists()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("become-guide").assertExists()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    @Test
    fun `ads are off under tests, and forms go to the configured address`() {
        assertFalse(AdsConfigUnderTest.enabled)
        assertEquals(BuildConfig.APPLICATIONS_EMAIL, AppModule.dataConfig().applicationsEmail)
    }

    private object AdsConfigUnderTest {
        val enabled get() = AppModule.adsConfig().enabled
    }

    private fun hasTag(tag: String) = androidx.compose.ui.test.hasTestTag(tag)

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
