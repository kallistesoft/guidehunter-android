package ca.kallistesoft.guidehunter.core.testing

import android.app.Activity
import ca.kallistesoft.guidehunter.core.ads.AdConsent
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
