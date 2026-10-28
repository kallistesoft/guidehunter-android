package ca.kallistesoft.guidehunter.feature.becomeguide

import android.app.Application
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.ads.RewardOutcome
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.OfferStatus
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeNativeAd
import ca.kallistesoft.guidehunter.core.testing.FakeNativeAds
import ca.kallistesoft.guidehunter.core.testing.FakeRewardedAds
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.ui.date
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
    private val nativeAds = FakeNativeAds(FakeNativeAd(headline = "Rangefinders"))
    private val ads = FakeRewardedAds()

    private fun str(id: Int, vararg args: Any) = app.getString(id, *args)

    private fun launch() {
        val viewModel = MyOffersViewModel(auth, account, ads, { TestGuides.today }, { TestGuides.now }, nativeAds)
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
    fun `an ad card shows below the offers`() {
        account.give(uid, GuideOffer("live", TestGuides.offer, OfferStatus.PUBLISHED, live = true))
        launch()
        compose.onNodeWithTag("native-ad").performScrollTo()
        compose.onNodeWithText("Rangefinders").assertExists()
    }

    @Test
    fun `no ad card without an ad, nor while the guide has no offers`() {
        launch()
        compose.onNodeWithTag("no-offers").assertExists()
        compose.onNodeWithTag("native-ad").assertDoesNotExist()
        assertEquals("not even requested", 0, nativeAds.loads)

        account.give(uid, GuideOffer("live", TestGuides.offer, OfferStatus.PUBLISHED, live = true))
        nativeAds.ad = null
        compose.onNodeWithTag("offer-live").performScrollTo()
        compose.onNodeWithTag("native-ad").assertDoesNotExist()
        assertEquals(1, nativeAds.loads)
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

    private fun day(instant: Instant) = app.date(instant.toLocalDateTime(TimeZone.currentSystemDefault()).date)

    /** A text in the live offer's card (its texts merge into the card, a button). */
    private fun inCard(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("offer-live").performScrollTo()
        return compose.onNodeWithTag(tag, useUnmergedTree = true)
    }

    private fun liveOffer(boostedUntil: Instant? = null) =
        GuideOffer("live", TestGuides.offer, OfferStatus.PUBLISHED, live = true, boostedUntil = boostedUntil)

    @Test
    fun `an offer on the map can be boosted after an ad, and then shows it's featured`() {
        account.give(uid, liveOffer(), GuideOffer("new", TestGuides.offer.copy(title = "Walleye")))
        launch()
        inCard("boost-note-live").assertTextEquals(str(R.string.boost_pitch))
        compose.onNodeWithTag("boost-new").assertDoesNotExist()
        compose.onNodeWithTag("featured-live", useUnmergedTree = true).assertDoesNotExist()

        // The explanation, then "Not now".
        compose.onNodeWithTag("boost-live").performClick()
        compose.onNodeWithText(str(R.string.boost_title, TestGuides.offer.title)).assertExists()
        assertEquals("the ad loads meanwhile", 1, ads.preloads)
        compose.onNodeWithTag("boost-not-now").performClick()
        compose.onNodeWithTag("boost-sheet").assertDoesNotExist()
        assertEquals(0, ads.shown)

        // Again, and watch the ad.
        compose.onNodeWithTag("boost-live").performScrollTo().performClick()
        compose.onNodeWithTag("boost-watch-ad").performClick()
        compose.onNodeWithTag("boost-sheet").assertDoesNotExist()
        assertEquals(1, ads.shown)
        inCard("boost-note-live")
            .assertTextEquals(str(R.string.boost_until, day(TestGuides.now + 3.days)))
        compose.onNodeWithTag("featured-live", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("boost-live").assertDoesNotExist()
        assertEquals("not opened for editing", emptyList<String>(), opened)
    }

    @Test
    fun `boost again shows once less than a day is left`() {
        account.give(uid, liveOffer(TestGuides.now + 12.hours))
        launch()
        compose.onNodeWithTag("featured-live", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("boost-live").performScrollTo().assertTextEquals(str(R.string.boost_again))
    }

    @Test
    fun `an ended boost says when, and can be boosted again`() {
        account.give(uid, liveOffer(TestGuides.now - 2.days))
        launch()
        inCard("boost-note-live")
            .assertTextEquals(str(R.string.boost_ended, day(TestGuides.now - 2.days)))
        compose.onNodeWithTag("featured-live", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("boost-live").assertTextEquals(str(R.string.boost_again))
    }

    @Test
    fun `a boost under way shows progress, and a failed one says why`() {
        account.give(uid, liveOffer())
        ads.onShow = CompletableDeferred()
        ads.outcome = RewardOutcome.NOT_EARNED
        launch()
        compose.onNodeWithTag("boost-live").performScrollTo().performClick()
        compose.onNodeWithTag("boost-watch-ad").performClick()
        compose.onNodeWithTag("boosting-live").assertExists()
        compose.onNodeWithTag("boost-live").assertDoesNotExist()
        ads.onShow?.complete(Unit)
        inCard("boost-error-live").assertTextEquals(str(R.string.boost_error_not_earned))

        ads.outcome = RewardOutcome.OFFLINE
        compose.onNodeWithTag("boost-live").performScrollTo().performClick()
        compose.onNodeWithTag("boost-watch-ad").performClick()
        inCard("boost-error-live").assertTextEquals(str(R.string.boost_error_offline))

        account.failure = IllegalStateException("denied")
        ads.outcome = RewardOutcome.EARNED
        compose.onNodeWithTag("boost-live").performScrollTo().performClick()
        compose.onNodeWithTag("boost-watch-ad").performClick()
        inCard("boost-error-live").assertTextEquals(str(R.string.boost_error_failed))
    }

    @Test
    @Config(qualifiers = "fr-w411dp-h891dp")
    fun `in French, a featured offer says "En vedette"`() {
        account.give(uid, liveOffer(TestGuides.now + 2.days))
        launch()
        compose.onNodeWithText("En vedette", useUnmergedTree = true).assertExists()
    }
}
