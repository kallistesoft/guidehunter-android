package ca.kallistesoft.guidehunter.feature.becomeguide

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.OfferStatus
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The guide's home: their profile and offers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp")
class MyOffersScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val uid = TestGuides.user.uid
    private val auth = FakeAuthRepository(TestGuides.user)
    private val account = FakeGuideAccountRepository()
    private val opened = mutableListOf<String>()

    private fun str(id: Int, vararg args: Any) = app.getString(id, *args)

    private fun launch() {
        val viewModel = MyOffersViewModel(auth, account, { TestGuides.today })
        compose.setContent {
            GuideHunterTheme {
                MyOffersScreen(
                    onSettings = { opened += "settings" },
                    onEditProfile = { opened += "profile" },
                    onAddOffer = { opened += "new" },
                    onEditOffer = { opened += it },
                    viewModel = viewModel,
                )
            }
        }
    }

    @Test
    fun `a new guide fills in the profile, then adds an offer`() {
        launch()
        compose.onNodeWithText(str(R.string.my_offers_title)).assertExists()
        compose.onNodeWithText(str(R.string.profile_missing)).assertExists()
        compose.onNodeWithTag("no-offers").assertExists()
        compose.onNodeWithTag("edit-profile").performClick()
        compose.onNodeWithTag("add-offer").performScrollTo().performClick()
        compose.onNodeWithTag("settings").performClick()
        assertEquals(listOf("profile", "new", "settings"), opened)
    }

    @Test
    fun `the offers show where they are in the review, and open to edit`() {
        account.profiles.value = mapOf(uid to TestGuides.profile.copy(website = "example.com"))
        account.give(
            uid,
            GuideOffer("new", TestGuides.offer.copy(title = "Walleye")),
            GuideOffer("live", TestGuides.offer, OfferStatus.PUBLISHED, live = true),
            GuideOffer("edited", TestGuides.offer.copy(title = "Moose"), OfferStatus.PENDING, live = true),
        )
        launch()
        compose.onNodeWithText(TestGuides.profile.businessName).assertExists()
        compose.onNodeWithText("example.com").assertExists()
        compose.onNodeWithText(str(R.string.status_pending)).performScrollTo()
        compose.onNodeWithText(str(R.string.status_published)).performScrollTo()
        compose.onNodeWithText(str(R.string.status_changes_pending)).performScrollTo()
        compose.onNodeWithTag("offer-live").performScrollTo().performClick()
        compose.onNodeWithTag("edit-profile").performScrollTo().performClick()
        assertEquals(listOf("live", "profile"), opened)
    }
}
