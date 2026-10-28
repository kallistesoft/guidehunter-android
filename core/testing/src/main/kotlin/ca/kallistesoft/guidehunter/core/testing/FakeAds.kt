package ca.kallistesoft.guidehunter.core.testing

import android.app.Activity
import androidx.compose.ui.graphics.ImageBitmap
import ca.kallistesoft.guidehunter.core.ads.AdConsent
import ca.kallistesoft.guidehunter.core.ads.NativeAdHandle
import ca.kallistesoft.guidehunter.core.ads.NativeAds
import ca.kallistesoft.guidehunter.core.ads.RewardOutcome
import ca.kallistesoft.guidehunter.core.ads.RewardedAds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** Consent that a test sets: whether the app offers the privacy options, and how often they were opened. */
class FakeAdConsent(privacyOptionsRequired: Boolean = false) : AdConsent {
    override val privacyOptionsRequired = MutableStateFlow(privacyOptionsRequired)
    var gathered = 0
        private set
    var privacyOptionsShown = 0
        private set

    override fun gatherConsent(activity: Activity) {
        gathered++
    }

    override fun showPrivacyOptions() {
        privacyOptionsShown++
    }
}

/**
 * Rewarded ads that answer [outcome]; counts the preloads and the ads asked for. With [onShow] set, the ad stays on
 * screen until it's completed.
 */
class FakeRewardedAds(var outcome: RewardOutcome = RewardOutcome.EARNED) : RewardedAds {
    var preloads = 0
        private set
    var shown = 0
        private set
    var onShow: CompletableDeferred<Unit>? = null

    override fun preload() {
        preloads++
    }

    override suspend fun showForReward(): RewardOutcome {
        shown++
        onShow?.await()
        return outcome
    }
}

/** A native ad with plain assets; counts how often it was destroyed. */
class FakeNativeAd(
    override val headline: String = "Bushnell rangefinders",
    override val body: String? = "See farther in the bush.",
    override val callToAction: String? = "Shop now",
    override val icon: ImageBitmap? = null,
) : NativeAdHandle {
    var destroyed = 0
        private set

    override fun destroy() {
        destroyed++
    }
}

/** Native ads that serve [ad] (null: no ad, as with ads off or no fill); counts the loads. */
class FakeNativeAds(var ad: NativeAdHandle? = FakeNativeAd()) : NativeAds {
    var loads = 0
        private set

    override suspend fun load(): NativeAdHandle? {
        loads++
        return ad
    }
}
