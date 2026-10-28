package ca.kallistesoft.guidehunter.core.ads

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentInformation.OnConsentInfoUpdateFailureListener
import com.google.android.ump.ConsentInformation.OnConsentInfoUpdateSuccessListener
import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus
import com.google.android.ump.UserMessagingPlatform
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The SDK adapters: each SDK callback ends the matching suspend call. The SDKs themselves are mocked. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoogleAdsTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val activity = mockk<Activity>()

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `consent update ends after the form, or at once on an error`() = runTest {
        mockkStatic(UserMessagingPlatform::class)
        val info = mockk<ConsentInformation>()
        val success = slot<OnConsentInfoUpdateSuccessListener>()
        val failure = slot<OnConsentInfoUpdateFailureListener>()
        every { info.requestConsentInfoUpdate(activity, any(), capture(success), capture(failure)) } returns Unit
        val dismissed = slot<ConsentForm.OnConsentFormDismissedListener>()
        every { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, capture(dismissed)) } returns Unit
        val consent = UmpConsent(info)

        val afterForm = async { consent.update(activity) }
        runCurrent()
        success.captured.onConsentInfoUpdateSuccess()
        assertFalse("waits for the form", afterForm.isCompleted)
        dismissed.captured.onConsentFormDismissed(null)
        afterForm.await()

        val offline = async { consent.update(activity) }
        runCurrent()
        failure.captured.onConsentInfoUpdateFailure(mockk())
        offline.await()
        verify(exactly = 1) { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, any()) }
    }

    @Test
    fun `consent state and privacy options come from the consent information`() = runTest {
        mockkStatic(UserMessagingPlatform::class)
        val info = mockk<ConsentInformation> {
            every { canRequestAds() } returns true
            every { privacyOptionsRequirementStatus } returnsMany
                listOf(PrivacyOptionsRequirementStatus.REQUIRED, PrivacyOptionsRequirementStatus.NOT_REQUIRED)
        }
        val dismissed = slot<ConsentForm.OnConsentFormDismissedListener>()
        every { UserMessagingPlatform.showPrivacyOptionsForm(activity, capture(dismissed)) } returns Unit
        val consent = UmpConsent(info)

        assertTrue(consent.canRequestAds())
        assertTrue(consent.privacyOptionsRequired())
        assertFalse(consent.privacyOptionsRequired())

        val form = async { consent.showPrivacyOptions(activity) }
        runCurrent()
        assertFalse(form.isCompleted)
        dismissed.captured.onConsentFormDismissed(null)
        form.await()
    }

    @Test
    fun `the SDK initialises`() = runTest {
        mockkStatic(MobileAds::class)
        val initialized = slot<OnInitializationCompleteListener>()
        every { MobileAds.initialize(context, capture(initialized)) } returns Unit
        val sdk = GoogleAdsSdk(context, StandardTestDispatcher(testScheduler))

        val init = async { sdk.initialize() }
        runCurrent()
        assertFalse(init.isCompleted)
        initialized.captured.onInitializationComplete(mockk())
        init.await()
    }

    @Test
    fun `a rewarded ad loads or doesn't`() = runTest {
        mockkStatic(RewardedAd::class)
        val loaded = slot<RewardedAdLoadCallback>()
        every { RewardedAd.load(context, "rewarded-1", any<AdRequest>(), capture(loaded)) } returns Unit
        val sdk = GoogleAdsSdk(context, StandardTestDispatcher(testScheduler))

        val filled = async { sdk.loadRewarded("rewarded-1") }
        runCurrent()
        loaded.captured.onAdLoaded(mockk())
        assertNotNull(filled.await())

        val noFill = async { sdk.loadRewarded("rewarded-1") }
        runCurrent()
        loaded.captured.onAdFailedToLoad(mockk<LoadAdError>())
        assertNull(noFill.await())
    }

    @Test
    fun `a native ad loads or doesn't, and one arriving after the card left is destroyed`() = runTest {
        mockkConstructor(AdLoader.Builder::class)
        val loaded = slot<NativeAd.OnNativeAdLoadedListener>()
        val listener = slot<AdListener>()
        val loader = mockk<AdLoader>(relaxed = true)
        every { anyConstructed<AdLoader.Builder>().forNativeAd(capture(loaded)) } answers { self as AdLoader.Builder }
        every { anyConstructed<AdLoader.Builder>().withAdListener(capture(listener)) } answers { self as AdLoader.Builder }
        every { anyConstructed<AdLoader.Builder>().build() } returns loader
        val sdk = GoogleAdsSdk(context, StandardTestDispatcher(testScheduler))
        val ad = mockk<NativeAd>(relaxed = true)

        val filled = async { sdk.loadNative("native-1") }
        runCurrent()
        loaded.captured.onNativeAdLoaded(ad)
        assertSame(ad, (filled.await() as GoogleNativeAd).ad)

        val noFill = async { sdk.loadNative("native-1") }
        runCurrent()
        listener.captured.onAdFailedToLoad(mockk<LoadAdError>())
        assertNull(noFill.await())

        val left = async { sdk.loadNative("native-1") }
        runCurrent()
        left.cancel()
        loaded.captured.onNativeAdLoaded(ad)
        verify(exactly = 1) { ad.destroy() }
        verify(exactly = 3) { loader.loadAd(any<AdRequest>()) }
    }

    @Test
    fun `a native ad shows AdMob's assets and is destroyed with its card`() {
        val icon = mockk<NativeAd.Image> {
            every { drawable } returns BitmapDrawable(context.resources, Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        }
        val ad = mockk<NativeAd>(relaxed = true) {
            every { headline } returns "Rangefinders"
            every { body } returns "See farther."
            every { callToAction } returns "Shop"
            every { this@mockk.icon } returns icon
        }
        val native = GoogleNativeAd(ad)

        assertEquals("Rangefinders", native.headline)
        assertEquals("See farther.", native.body)
        assertEquals("Shop", native.callToAction)
        assertEquals(4, native.icon?.width)
        native.destroy()
        verify { ad.destroy() }

        val bare = mockk<NativeAd>(relaxed = true) {
            every { headline } returns null
            every { this@mockk.icon } returns null
        }
        assertEquals("", GoogleNativeAd(bare).headline)
        assertNull(GoogleNativeAd(bare).icon)
    }

    @Test
    fun `a rewarded ad says whether it was watched to the end`() = runTest {
        val callback = slot<FullScreenContentCallback>()
        val earned = slot<OnUserEarnedRewardListener>()
        val ad = mockk<RewardedAd> {
            every { fullScreenContentCallback = capture(callback) } returns Unit
            every { show(activity, capture(earned)) } returns Unit
        }
        val rewarded = GoogleRewarded(ad)

        val watched = async { rewarded.show(activity) }
        runCurrent()
        earned.captured.onUserEarnedReward(mockk())
        callback.captured.onAdDismissedFullScreenContent()
        assertTrue(watched.await())

        val closedEarly = async { rewarded.show(activity) }
        runCurrent()
        callback.captured.onAdDismissedFullScreenContent()
        assertFalse(closedEarly.await())

        val failed = async { rewarded.show(activity) }
        runCurrent()
        callback.captured.onAdFailedToShowFullScreenContent(mockk<AdError>())
        assertFalse(failed.await())
    }
}
