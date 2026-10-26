package ca.kallistesoft.guidehunter.feature.map

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.ui.LinkOpener
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Each contact state of a guide's offer, and what its button does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp")
class GuideDetailTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private var unlocks = 0

    private fun show(contact: ContactState, item: GuideItem = GuideItem(TestGuides.bear, distanceKm = 470)) {
        compose.setContent {
            GuideHunterTheme { GuideDetail(SelectedGuide(item, contact), { unlocks++ }, LinkOpener(app)) }
        }
    }

    @Test
    fun `the offer shows its price, place and distance`() {
        show(ContactState.Locked)
        compose.onNodeWithText(app.getString(R.string.price_from, "CA$2,400"), useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Abitibi · " + app.getString(R.string.distance_km, "470")).assertExists()
        compose.onNodeWithText(app.getString(ca.kallistesoft.guidehunter.core.ui.R.string.species_black_bear)).assertExists()
    }

    @Test
    fun `loading shows progress`() {
        show(ContactState.Unlocking)
        compose.onNodeWithText(app.getString(R.string.contact_unlocking)).performScrollTo()
        compose.onNodeWithTag("contact-loading").assertExists()
        compose.onNodeWithTag("unlock").assertDoesNotExist()
    }

    @Test
    fun `an ad closed early offers the ad again`() {
        show(ContactState.Failed(UnlockError.NOT_EARNED))
        compose.onNodeWithText(app.getString(R.string.contact_not_earned)).performScrollTo()
        compose.onNodeWithTag("unlock").performScrollTo().performClick()
        assertEquals(1, unlocks)
    }

    @Test
    fun `offline says so`() {
        show(ContactState.Failed(UnlockError.OFFLINE))
        compose.onNodeWithText(app.getString(R.string.contact_offline)).performScrollTo()
        compose.onNodeWithTag("unlock").performScrollTo().performClick()
        assertEquals("the ad is offered again once online", 1, unlocks)
    }

    @Test
    fun `a failed load is tried again without an ad`() {
        show(ContactState.Failed(UnlockError.LOAD_FAILED))
        compose.onNodeWithText(app.getString(R.string.contact_load_failed)).performScrollTo()
        compose.onNodeWithText(app.getString(R.string.try_again)).performScrollTo().performClick()
        assertEquals(1, unlocks)
    }

    @Test
    fun `a guide without contact details says so`() {
        show(ContactState.Missing)
        compose.onNodeWithText(app.getString(R.string.contact_missing)).performScrollTo()
    }

    @Test
    fun `only the contact details given are shown`() {
        show(ContactState.Unlocked(GuideContact("Camp")), GuideItem(TestGuides.salmon))
        compose.onNodeWithTag("contact-name").performScrollTo()
        compose.onNodeWithTag("contact-phone").assertDoesNotExist()
        compose.onNodeWithTag("contact-email").assertDoesNotExist()
        compose.onNodeWithTag("contact-website").assertDoesNotExist()
        compose.onNodeWithText("Gaspé").assertExists()
    }
}
