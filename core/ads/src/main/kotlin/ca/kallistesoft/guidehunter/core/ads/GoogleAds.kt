package ca.kallistesoft.guidehunter.core.ads

import android.app.Activity
import android.content.Context
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** The consent SDK, as [AdsManager] needs it. Errors (offline, no form configured) only end the call. */
internal interface ConsentGateway {
    /** Refreshes the consent information, then shows the consent form if the user's region requires it. */
    suspend fun update(activity: Activity)

    fun canRequestAds(): Boolean

    fun privacyOptionsRequired(): Boolean

    suspend fun showPrivacyOptions(activity: Activity)
}

/** The Mobile Ads SDK, as [AdsManager] needs it. */
internal interface AdsSdk {
    suspend fun initialize()

    /** A loaded rewarded ad, or null when none could be loaded (no fill, offline). */
    suspend fun loadRewarded(unitId: String): Rewarded?

    /** A loaded native ad, or null when none could be loaded (no fill, offline). */
    suspend fun loadNative(unitId: String): NativeAdHandle?
}

internal fun interface Rewarded {
    /** Shows the ad full screen and returns when it's closed: true if the reward was earned. False if it can't show. */
    suspend fun show(activity: Activity): Boolean
}

/** Google's User Messaging Platform. */
internal class UmpConsent(private val info: ConsentInformation) : ConsentGateway {
    override suspend fun update(activity: Activity) = suspendCancellableCoroutine { done ->
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { done.resume(Unit) } },
            { done.resume(Unit) },
        )
    }

    override fun canRequestAds(): Boolean = info.canRequestAds()

    override fun privacyOptionsRequired(): Boolean =
        info.privacyOptionsRequirementStatus == PrivacyOptionsRequirementStatus.REQUIRED

    override suspend fun showPrivacyOptions(activity: Activity) = suspendCancellableCoroutine { done ->
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { done.resume(Unit) }
    }
}

/** Google Mobile Ads. Initialised off the main thread ([background]), as Google recommends. */
internal class GoogleAdsSdk(
    private val context: Context,
    private val background: CoroutineDispatcher = Dispatchers.IO,
) : AdsSdk {
    override suspend fun initialize() = withContext(background) {
        suspendCancellableCoroutine { done -> MobileAds.initialize(context) { done.resume(Unit) } }
    }

    override suspend fun loadRewarded(unitId: String): Rewarded? = suspendCancellableCoroutine { done ->
        RewardedAd.load(
            context,
            unitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) = done.resume(GoogleRewarded(ad))

                override fun onAdFailedToLoad(error: LoadAdError) = done.resume(null)
            },
        )
    }

    /** An ad that arrives after the card left (the load was cancelled) is destroyed at once. */
    override suspend fun loadNative(unitId: String): NativeAdHandle? = suspendCancellableCoroutine { done ->
        AdLoader.Builder(context, unitId)
            .forNativeAd { ad -> done.resume(GoogleNativeAd(ad)) { _, _, _ -> ad.destroy() } }
            .withAdListener(
                object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) = done.resume(null)
                },
            )
            .build()
            .loadAd(AdRequest.Builder().build())
    }
}

/** An AdMob native ad; [NativeAdCard] binds [ad] to AdMob's view, which tracks its impressions and clicks. */
internal class GoogleNativeAd(val ad: NativeAd) : NativeAdHandle {
    override val headline: String get() = ad.headline.orEmpty()
    override val body: String? get() = ad.body
    override val callToAction: String? get() = ad.callToAction
    override val icon by lazy { ad.icon?.drawable?.toBitmap()?.asImageBitmap() }

    override fun destroy() = ad.destroy()
}

internal class GoogleRewarded(private val ad: RewardedAd) : Rewarded {
    override suspend fun show(activity: Activity): Boolean = suspendCancellableCoroutine { done ->
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = done.resume(earned)

            override fun onAdFailedToShowFullScreenContent(error: AdError) = done.resume(false)
        }
        ad.show(activity) { earned = true }
    }
}
