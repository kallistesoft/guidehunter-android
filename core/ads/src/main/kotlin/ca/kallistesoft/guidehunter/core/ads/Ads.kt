package ca.kallistesoft.guidehunter.core.ads

import android.app.Activity
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

/** A rewarded ad the user chooses to watch to see a guide's name and contact details. */
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
 * The rewarded ad unit; the build type chooses it (Google's test unit in debug, see app/build.gradle.kts). [enabled]
 * false turns ads off entirely, consent request included (instrumented tests): every reward is then given.
 */
data class AdsConfig(val rewardedUnitId: String, val enabled: Boolean = true)
