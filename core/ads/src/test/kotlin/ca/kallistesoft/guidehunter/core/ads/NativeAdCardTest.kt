package ca.kallistesoft.guidehunter.core.ads

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.testing.FakeNativeAd
import ca.kallistesoft.guidehunter.core.testing.FakeNativeAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.dynamic.ObjectWrapper
import com.google.android.gms.ads.nativead.NativeAdView
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The native ad card: what it shows, nothing without an ad, the ad freed when it leaves, AdMob's view bound. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp")
class NativeAdCardTest {
    @get:Rule
    val compose = createComposeRule()

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `the card shows the ad's assets under an Ad label`() {
        val ads = FakeNativeAds(FakeNativeAd(headline = "Rangefinders", body = "See farther.", callToAction = "Shop", icon = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).asImageBitmap()))
        compose.setContent { GuideHunterTheme { NativeAdCard(ads) } }

        compose.onNodeWithTag("native-ad").assertExists()
        compose.onNodeWithTag("ad-label").assertExists()
        compose.onNodeWithText("Ad").assertExists()
        compose.onNodeWithText("Rangefinders").assertExists()
        compose.onNodeWithText("See farther.").assertExists()
        compose.onNodeWithText("Shop").assertExists()
        assertEquals(1, ads.loads)
    }

    @Test
    @Config(qualifiers = "fr-w411dp-h891dp")
    fun `in French the label says Annonce`() {
        compose.setContent { GuideHunterTheme { NativeAdContent("Télémètres", body = null, callToAction = null, icon = null) } }

        compose.onNodeWithText("Annonce").assertExists()
        compose.onNodeWithText("Télémètres").assertExists()
        compose.onNodeWithTag("ad-call-to-action").assertDoesNotExist()
    }

    @Test
    fun `without an ad the card draws nothing`() {
        val ads = FakeNativeAds(ad = null)
        compose.setContent { GuideHunterTheme { NativeAdCard(ads) } }

        compose.onNodeWithTag("native-ad").assertDoesNotExist()
        assertEquals(1, ads.loads)
    }

    @Test
    fun `the ad is destroyed when the card leaves the screen`() {
        val ad = FakeNativeAd()
        var shown by mutableStateOf(true)
        compose.setContent { GuideHunterTheme { if (shown) NativeAdCard(FakeNativeAds(ad)) } }
        compose.onNodeWithTag("native-ad").assertExists()
        assertEquals(0, ad.destroyed)

        shown = false
        compose.waitForIdle()
        assertEquals(1, ad.destroyed)
    }

    @Test
    fun `an AdMob ad is drawn in AdMob's view, with each asset registered`() {
        val native = mockk<NativeAd>(relaxed = true) {
            every { headline } returns "Rangefinders"
            every { body } returns "See farther."
            every { callToAction } returns "Shop"
            every { icon } returns null
        }
        // setNativeAd hands the SDK the ad's internal wrapper (NativeAd's obfuscated zza): a mocked one here.
        val wrapper = ObjectWrapper.wrap(Any())
        every { native["zza"]() } returns wrapper
        val ads = FakeNativeAds(GoogleNativeAd(native))
        lateinit var root: View
        compose.setContent {
            GuideHunterTheme {
                root = androidx.compose.ui.platform.LocalView.current
                NativeAdCard(ads)
            }
        }
        compose.waitForIdle()

        val adView = assertNotNull(root.findAdView())
        assertNotNull(adView.headlineView)
        assertNotNull(adView.bodyView)
        assertNotNull(adView.callToActionView)
        verify { native["zza"]() }
        compose.onNodeWithText("Rangefinders").assertExists()
        compose.onNodeWithText("Ad").assertExists()
    }

    private fun View.findAdView(): NativeAdView? = when (this) {
        is NativeAdView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findAdView() }
        else -> null
    }

    private fun <T : Any> assertNotNull(value: T?): T = value.also { org.junit.Assert.assertNotNull(it) }!!
}
