package ca.kallistesoft.guidehunter.feature.becomeguide

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
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
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The guide's space with a stand-in Google sheet ([FakeGoogleSignIn]). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp")
class MyOffersScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val uid = TestGuides.user.uid
    private val auth = FakeAuthRepository()
    private val account = FakeGuideAccountRepository()
    private val google = FakeGoogleSignIn()
    private val opened = mutableListOf<String>()

    private fun str(id: Int, vararg args: Any) = app.getString(id, *args)

    private fun launch() {
        val viewModel = MyOffersViewModel(auth, account, google)
        compose.setContent {
            GuideHunterTheme {
                MyOffersScreen(
                    onBack = { opened += "back" },
                    onEditProfile = { opened += "profile" },
                    onAddOffer = { opened += "new" },
                    onEditOffer = { opened += it },
                    viewModel = viewModel,
                )
            }
        }
    }

    @Test
    fun `a guide signs in with Google, fills in the profile, then adds an offer`() {
        launch()
        compose.onNodeWithText(str(R.string.become_guide_title)).assertExists()
        compose.onNodeWithTag("account-menu").assertDoesNotExist()
        compose.onNodeWithTag("sign-in").performClick()
        compose.onNodeWithText(str(R.string.my_offers_title)).assertExists()
        compose.onNodeWithText(str(R.string.signed_in_as, "guide@example.com")).assertExists()
        compose.onNodeWithText(str(R.string.profile_missing)).assertExists()
        compose.onNodeWithTag("no-offers").assertExists()
        compose.onNodeWithTag("edit-profile").performClick()
        compose.onNodeWithTag("add-offer").performScrollTo().performClick()
        compose.onNodeWithTag("back").performClick()
        assertEquals(listOf("profile", "new", "back"), opened)
    }

    @Test
    fun `the offers show where they are in the review, and open to edit`() {
        auth.user.value = TestGuides.user
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

    @Test
    fun `the button waits while signing in, and a failure says so until dismissed`() {
        auth.onSignIn = CompletableDeferred()
        launch()
        compose.onNodeWithTag("sign-in").performClick()
        compose.onNodeWithText(str(R.string.signing_in)).assertExists()
        compose.onNodeWithTag("sign-in").assertIsNotEnabled()
        auth.failure = IllegalStateException()
        auth.onSignIn?.complete(Unit)
        compose.onNodeWithText(str(R.string.sign_in_failed)).assertExists()
        compose.onNodeWithTag("dismiss-error").performClick()
        compose.onNodeWithText(str(R.string.sign_in_failed)).assertDoesNotExist()
    }

    @Test
    fun `a build without Firebase says guides can't sign in`() {
        google.failure = ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException()
        launch()
        compose.onNodeWithTag("sign-in").performClick()
        compose.onNodeWithText(str(R.string.sign_in_not_configured)).assertExists()
    }

    @Test
    fun `sign out from the menu`() {
        auth.user.value = TestGuides.user
        launch()
        compose.onNodeWithTag("account-menu").performClick()
        compose.onNodeWithTag("sign-out").performClick()
        compose.onNodeWithTag("sign-in").assertExists()
        assertEquals(1, google.signedOut)
    }

    @Test
    fun `delete the account, after a confirmation`() {
        auth.user.value = TestGuides.user
        account.give(uid, GuideOffer("live", TestGuides.offer, OfferStatus.PUBLISHED, live = true))
        launch()
        compose.onNodeWithTag("account-menu").performClick()
        compose.onNodeWithTag("delete-account").performClick()
        compose.onNodeWithText(str(R.string.delete_account_body)).assertExists()
        compose.onNodeWithTag("delete-account-cancel").performClick()
        compose.onNodeWithText(str(R.string.delete_account_body)).assertDoesNotExist()

        compose.onNodeWithTag("account-menu").performClick()
        compose.onNodeWithTag("delete-account").performClick()
        compose.onNodeWithTag("delete-account-confirm").performClick()
        compose.onNodeWithTag("sign-in").assertExists()
        assertTrue(auth.deleted)
    }

    @Test
    fun `a failed deletion says so`() {
        auth.user.value = TestGuides.user
        google.failure = IllegalStateException()
        launch()
        compose.onNodeWithTag("account-menu").performClick()
        compose.onNodeWithTag("delete-account").performClick()
        compose.onNodeWithTag("delete-account-confirm").performClick()
        compose.onNodeWithText(str(R.string.delete_account_failed)).assertExists()
    }
}
