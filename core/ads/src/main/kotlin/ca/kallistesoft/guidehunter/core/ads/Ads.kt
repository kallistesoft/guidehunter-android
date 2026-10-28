package ca.kallistesoft.guidehunter.core.ads

import android.app.Activity
import androidx.compose.ui.graphics.ImageBitmap
import javax.inject.Qualifier
import kotlinx.coroutines.flow.StateFlow

/** What came of asking for a rewarded ad. */
enum class RewardOutcome {
    /** The ad was watched to the end: give the reward. */
    EARNED,

    /** The ad was closed before the end: no reward. */
    NOT_EARNED,

    /** No network, so no ad: no reward either (letting it through would teach users to go offline). */
    OFFLINE,

    /** No ad to watch through no fault of the user (ads turned off by us, or none available online): give the reward. */
    NO_ADS,
}

/** A rewarded ad the user chooses to watch: to see a guide's name and contact details, or to boost an offer. */
interface RewardedAds {
    /** Starts loading one ahead of time (a guide's offer opens), so it shows without waiting. */
    fun preload()

    /** Shows a rewarded ad and returns once it's closed, waiting a few seconds for one still loading. */
    suspend fun showForReward(): RewardOutcome
}

/** The consent to ads (Google's User Messaging Platform), asked at launch. */
interface AdConsent {
    /** The user's region requires a way back to their consent choices: the app shows "Privacy options" while true. */
    val privacyOptionsRequired: StateFlow<Boolean>

    /** At launch: refreshes the consent information and shows the consent form if it's required. */
    fun gatherConsent(activity: Activity)

    /** Shows the consent choices again, over the app's current screen. */
    fun showPrivacyOptions()
}

/**
 * Native ads, shown as a card in the app's lists ([NativeAdCard]); never on the map or a guide's offer. They never
 * block anything: no ad simply means no card.
 */
interface NativeAds {
    /**
     * A native ad, or null when ads are off ([AdsConfig.enabled], no native unit), consent doesn't allow requesting
     * ads, or none could be loaded (no fill, offline). The caller [destroys][NativeAdHandle.destroy] it once off screen.
     */
    suspend fun load(): NativeAdHandle?
}

/** A loaded native ad: the assets the card shows. */
interface NativeAdHandle {
    val headline: String
    val body: String?
    val callToAction: String?
    val icon: ImageBitmap?

    /** Frees the ad: called when its card leaves the screen. */
    fun destroy()
}

/**
 * The ad units; the build type chooses them (Google's test units in debug, see app/build.gradle.kts). [enabled]
 * false turns ads off entirely, consent request included (instrumented tests): every reward is then given, and no
 * native ad shows. A blank [nativeUnitId] turns native ads off. [boostUnitId] is the boost's rewarded unit, apart so
 * its revenue is reported on its own (the contact's [rewardedUnitId] when blank).
 */
data class AdsConfig(
    val rewardedUnitId: String,
    val nativeUnitId: String = "",
    val enabled: Boolean = true,
    val boostUnitId: String = "",
)

/** The rewarded ad a guide watches to boost an offer ([AdsConfig.boostUnitId]), injected as `@BoostAds RewardedAds`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BoostAds
