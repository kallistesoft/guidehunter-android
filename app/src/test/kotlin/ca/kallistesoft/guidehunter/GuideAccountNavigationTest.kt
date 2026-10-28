package ca.kallistesoft.guidehunter

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import ca.kallistesoft.guidehunter.core.data.AuthRepository
import ca.kallistesoft.guidehunter.core.data.GuideAccountRepository
import ca.kallistesoft.guidehunter.core.data.GuideRepository
import ca.kallistesoft.guidehunter.core.data.LocationRepository
import ca.kallistesoft.guidehunter.core.data.di.DataModule
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGuideRepository
import ca.kallistesoft.guidehunter.core.testing.FakeLocationRepository
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.feature.becomeguide.GoogleSignIn
import ca.kallistesoft.guidehunter.feature.becomeguide.GoogleSignInModule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The guide's screens in the real app, with fake repositories and Google sheet: from the map to the offers, the
 * profile and the offer form, and back.
 */
@HiltAndroidTest
@UninstallModules(DataModule::class, GoogleSignInModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "en-w411dp-h891dp")
class GuideAccountNavigationTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val uid = TestGuides.user.uid

    @BindValue @JvmField
    val auth: AuthRepository = FakeAuthRepository()

    @BindValue @JvmField
    val account: GuideAccountRepository = FakeGuideAccountRepository().apply {
        give(uid, GuideOffer("o1", TestGuides.offer))
    }

    @BindValue @JvmField
    val guides: GuideRepository = FakeGuideRepository()

    @BindValue @JvmField
    val location: LocationRepository = FakeLocationRepository()

    @BindValue @JvmField
    val google: GoogleSignIn = object : GoogleSignIn {
        override suspend fun idToken(activity: Context) = FakeAuthRepository.TOKEN

        override suspend fun signOut(activity: Context) = Unit
    }

    @Before
    fun setUp() = hilt.inject()

    private fun tag(tag: String) = compose.onNodeWithTag(tag)

    private fun waitFor(tag: String) = compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `sign in, fill in the profile, open an offer, add one, and back to the map`() {
        tag("become-guide").performClick()
        tag("sign-in").performClick()
        waitFor("profile")

        tag("edit-profile").performClick()
        waitFor("field-business-name")
        tag("field-business-name").performTextInput("Pourvoirie Test")
        tag("save").performScrollTo().performClick()
        waitFor("offer-o1")
        assertEquals("Pourvoirie Test", (account as FakeGuideAccountRepository).profiles.value[uid]?.businessName)

        tag("offer-o1").performClick()
        waitFor("delete-offer")
        tag("back").performClick()

        tag("add-offer").performScrollTo().performClick()
        waitFor("field-title")
        tag("location-picker").assertExists()
        tag("send").performScrollTo().performClick() // empty: errors, stays
        tag("back").performClick()

        waitFor("offer-o1")
        tag("back").performClick()
        tag("become-guide").assertExists()
    }

    @Test
    fun `an offer sent without a profile leads to the profile`() {
        (auth as FakeAuthRepository).user.value = TestGuides.user
        tag("become-guide").performClick()
        tag("offer-o1").performClick()
        waitFor("field-title")
        tag("send").performScrollTo().performClick()
        waitFor("needs-profile")
        tag("needs-profile").performScrollTo().performClick()
        waitFor("field-business-name")
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
