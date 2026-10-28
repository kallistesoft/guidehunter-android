package ca.kallistesoft.guidehunter.core.ads

import android.app.Activity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The ad rules, on top of the SDKs ([consent], [sdk]):
 * - nothing is initialised, loaded or shown until consent allows ads to be requested;
 * - a loaded ad older than [AD_LIFETIME_MILLIS] is dropped (AdMob doesn't serve stale ones) and loaded again;
 * - a native ad is only requested once consent allows it, and is simply missing otherwise;
 * - a rewarded ad is never skipped by going offline: no network means no reward. It's given without an ad only when
 *   the user can't cause it: ads turned off by us ([AdsConfig.enabled]), or no ad available while online.
 *
 * Runs on the main thread ([scope] and callers), as the SDKs require; [clock] is the elapsed real time in ms.
 */
internal class AdsManager(
    private val consent: ConsentGateway,
    private val sdk: AdsSdk,
    private val currentActivity: () -> Activity?,
    private val config: AdsConfig,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val isOnline: () -> Boolean,
) : RewardedAds, NativeAds, AdConsent {
    private val privacyRequired = MutableStateFlow(false)
    override val privacyOptionsRequired: StateFlow<Boolean> = privacyRequired.asStateFlow()

    private var initialized = false
    private var rewardedLoading: Deferred<Rewarded?>? = null
    private var rewarded: Rewarded? = null
    private var rewardedLoadedAt = 0L

    override fun gatherConsent(activity: Activity) {
        if (!config.enabled) return
        scope.launch {
            consent.update(activity)
            privacyRequired.value = consent.privacyOptionsRequired()
        }
    }

    override fun showPrivacyOptions() {
        val activity = currentActivity() ?: return
        scope.launch {
            consent.showPrivacyOptions(activity)
            privacyRequired.value = consent.privacyOptionsRequired()
        }
    }

    override fun preload() {
        scope.launch { if (adsOn() && consent.canRequestAds()) loadRewarded() }
    }

    override suspend fun showForReward(): RewardOutcome {
        if (!adsOn()) return RewardOutcome.NO_ADS
        if (!isOnline()) return RewardOutcome.OFFLINE
        val activity = currentActivity() ?: return RewardOutcome.NOT_EARNED
        // Consent not settled yet (the launch request failed offline): ask now rather than skip the ad.
        if (!consent.canRequestAds()) consent.update(activity)
        if (!consent.canRequestAds()) return RewardOutcome.NO_ADS
        val ad = withTimeoutOrNull(REWARDED_WAIT_MILLIS) { loadRewarded().await() }
        rewarded = null
        return when {
            ad != null -> if (ad.show(activity)) RewardOutcome.EARNED else RewardOutcome.NOT_EARNED
            isOnline() -> RewardOutcome.NO_ADS
            else -> RewardOutcome.OFFLINE
        }.also { preload() }
    }

    override suspend fun load(): NativeAdHandle? {
        if (!config.enabled || config.nativeUnitId.isBlank() || !consent.canRequestAds()) return null
        initialize()
        return sdk.loadNative(config.nativeUnitId)
    }

    /** The load in progress, the loaded rewarded ad (under [AD_LIFETIME_MILLIS] old), or a new load. */
    private fun loadRewarded(): Deferred<Rewarded?> {
        rewardedLoading?.takeIf { it.isActive }?.let { return it }
        rewarded?.takeIf { clock() - rewardedLoadedAt < AD_LIFETIME_MILLIS }?.let { return CompletableDeferred(it) }
        return scope.async {
            initialize()
            sdk.loadRewarded(config.rewardedUnitId).also {
                rewarded = it
                rewardedLoadedAt = clock()
            }
        }.also { rewardedLoading = it }
    }

    private suspend fun initialize() {
        if (!initialized) {
            initialized = true
            sdk.initialize()
        }
    }

    private fun adsOn(): Boolean = config.enabled && config.rewardedUnitId.isNotBlank()
}

/** A loaded ad is shown within the hour, or dropped. */
internal const val AD_LIFETIME_MILLIS = 60 * 60 * 1_000L

/** How long "Watch an ad" waits for a rewarded ad still loading. */
internal const val REWARDED_WAIT_MILLIS = 8_000L
