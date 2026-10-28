package ca.kallistesoft.guidehunter.core.ads

import android.app.Activity
import ca.kallistesoft.guidehunter.core.testing.FakeNativeAd
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ad rules on fake SDKs: consent gating, preloading, fallbacks; no network, no reward. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdsManagerTest {
    private val activity = mockk<Activity>()
    private val consent = FakeConsent()
    private val sdk = FakeSdk()
    private var now = 0L
    private var onScreen: Activity? = activity
    private var online = true

    private fun TestScope.manager(config: AdsConfig = AdsConfig("rewarded-1", nativeUnitId = "native-1")) =
        AdsManager(consent, sdk, { onScreen }, config, backgroundScope, { now }, { online })

    @Test
    fun `consent is asked at launch, unless ads are off`() = runTest {
        manager().gatherConsent(activity)
        runCurrent()
        assertEquals(1, consent.updates)

        manager(AdsConfig("rewarded-1", enabled = false)).gatherConsent(activity)
        runCurrent()
        assertEquals("no consent request with ads off", 1, consent.updates)
    }

    @Test
    fun `a native ad loads from the native unit once consent allows ads`() = runTest {
        consent.canRequest = false
        val ads = manager()
        assertNull("no consent, no request", ads.load())
        assertEquals(0 to 0, sdk.initialized to sdk.nativeLoads.size)

        consent.canRequest = true
        assertSame(sdk.nativeAd, ads.load())
        sdk.nativeAd = null
        assertNull("no fill: no card", ads.load())
        assertEquals(listOf("native-1", "native-1"), sdk.nativeLoads)
        assertEquals("initialised once", 1, sdk.initialized)
    }

    @Test
    fun `no native ad when ads are off or there's no native unit`() = runTest {
        assertNull(manager(AdsConfig("rewarded-1", nativeUnitId = "native-1", enabled = false)).load())
        assertNull(manager(AdsConfig("rewarded-1")).load())
        assertTrue(sdk.nativeLoads.isEmpty())
    }

    @Test
    fun `nothing loads before consent allows ads`() = runTest {
        consent.canRequest = false
        val ads = manager()
        ads.preload()
        runCurrent()
        assertEquals(0 to 0, sdk.initialized to sdk.rewardedLoads)
    }

    @Test
    fun `privacy options follow the consent, and the form shows on the current screen`() = runTest {
        consent.privacyRequired = true
        val ads = manager()
        ads.gatherConsent(activity)
        runCurrent()
        assertTrue(ads.privacyOptionsRequired.value)

        consent.onPrivacyOptions = { consent.privacyRequired = false }
        ads.showPrivacyOptions()
        runCurrent()
        assertEquals(listOf(activity), consent.privacyShownOn)
        assertFalse(ads.privacyOptionsRequired.value)
    }

    @Test
    fun `privacy options need a screen to show on`() = runTest {
        val ads = manager()
        onScreen = null
        ads.showPrivacyOptions()
        runCurrent()
        assertTrue(consent.privacyShownOn.isEmpty())
    }

    @Test
    fun `a rewarded ad watched to the end earns the reward, closed early it doesn't`() = runTest {
        val ads = manager()
        ads.preload()
        runCurrent()
        assertEquals(1 to 1, sdk.initialized to sdk.rewardedLoads)
        assertEquals(RewardOutcome.EARNED, ads.showForReward())
        assertEquals(listOf(activity), sdk.rewardedShownOn)
        runCurrent()
        assertEquals("the next one preloads", 2, sdk.rewardedLoads)

        sdk.earn = false
        assertEquals(RewardOutcome.NOT_EARNED, ads.showForReward())
        assertEquals(listOf("rewarded-1"), sdk.rewardedUnitIds.distinct())
        assertEquals("initialised once", 1, sdk.initialized)
    }

    @Test
    fun `offline there is no reward, even if an ad was loaded`() = runTest {
        val ads = manager()
        ads.preload()
        runCurrent()
        online = false
        assertEquals(RewardOutcome.OFFLINE, ads.showForReward())
        assertTrue(sdk.rewardedShownOn.isEmpty())
    }

    @Test
    fun `the network lost while loading means offline, no ad online means it's given`() = runTest {
        val ads = manager()
        sdk.fill = false
        sdk.onRewardedLoad = { online = false }
        assertEquals(RewardOutcome.OFFLINE, ads.showForReward())
        online = true
        sdk.onRewardedLoad = {}
        assertEquals(RewardOutcome.NO_ADS, ads.showForReward())
    }

    @Test
    fun `an ad still loading is waited for a few seconds, then given up on, and kept for next time`() = runTest {
        val ads = manager()
        sdk.waitOnLoad = CompletableDeferred()
        var outcome: RewardOutcome? = null
        backgroundScope.launch { outcome = ads.showForReward() }
        advanceTimeBy(REWARDED_WAIT_MILLIS - 1)
        assertEquals("still waiting", null, outcome)
        advanceTimeBy(2)
        assertEquals("online but nothing came: given", RewardOutcome.NO_ADS, outcome)

        sdk.waitOnLoad?.complete(Unit) // the late ad arrives
        runCurrent()
        assertEquals("no new load", RewardOutcome.EARNED, ads.showForReward())
        assertEquals(1, sdk.rewardedLoads)
    }

    @Test
    fun `ads turned off by us give the reward without an ad`() = runTest {
        assertEquals(RewardOutcome.NO_ADS, manager(AdsConfig("rewarded-1", enabled = false)).showForReward())
        assertEquals("no rewarded unit", RewardOutcome.NO_ADS, manager(AdsConfig("")).showForReward())
        online = false
        assertEquals("even offline", RewardOutcome.NO_ADS, manager(AdsConfig("")).showForReward())
        manager(AdsConfig("")).preload()
        runCurrent()
        assertEquals(0, sdk.rewardedLoads)
    }

    @Test
    fun `consent not settled is asked for before the ad`() = runTest {
        consent.canRequest = false
        val ads = manager()
        consent.onUpdate = { consent.canRequest = true }
        assertEquals(RewardOutcome.EARNED, ads.showForReward())
        assertEquals(1, consent.updates)

        consent.canRequest = false
        consent.onUpdate = {}
        assertEquals("still no consent: given", RewardOutcome.NO_ADS, ads.showForReward())
    }

    @Test
    fun `no screen to show on means no reward`() = runTest {
        val ads = manager()
        onScreen = null
        assertEquals(RewardOutcome.NOT_EARNED, ads.showForReward())
    }

    @Test
    fun `a rewarded ad loaded over an hour ago is loaded again`() = runTest {
        val ads = manager()
        ads.preload()
        runCurrent()
        now += 60 * 60_000
        ads.preload()
        runCurrent()
        assertEquals(2, sdk.rewardedLoads)
        ads.preload()
        runCurrent()
        assertEquals("a fresh one is kept", 2, sdk.rewardedLoads)
    }

    private class FakeConsent : ConsentGateway {
        var canRequest = true
        var privacyRequired = false
        var updates = 0
        var onUpdate: () -> Unit = {}
        var onPrivacyOptions: () -> Unit = {}
        val privacyShownOn = mutableListOf<Activity>()

        override suspend fun update(activity: Activity) {
            updates++
            onUpdate()
        }

        override fun canRequestAds() = canRequest

        override fun privacyOptionsRequired() = privacyRequired

        override suspend fun showPrivacyOptions(activity: Activity) {
            privacyShownOn += activity
            onPrivacyOptions()
        }
    }

    private class FakeSdk : AdsSdk {
        var initialized = 0
        var fill = true
        var waitOnLoad: CompletableDeferred<Unit>? = null
        var rewardedLoads = 0
        val rewardedUnitIds = mutableListOf<String>()
        val rewardedShownOn = mutableListOf<Activity>()
        var earn = true
        var onRewardedLoad: () -> Unit = {}
        val nativeLoads = mutableListOf<String>()
        var nativeAd: NativeAdHandle? = FakeNativeAd()

        override suspend fun initialize() {
            initialized++
        }

        override suspend fun loadNative(unitId: String): NativeAdHandle? {
            nativeLoads += unitId
            return nativeAd
        }

        override suspend fun loadRewarded(unitId: String): Rewarded? {
            rewardedLoads++
            rewardedUnitIds += unitId
            waitOnLoad?.await()
            onRewardedLoad()
            return if (fill) {
                Rewarded { activity ->
                    rewardedShownOn += activity
                    earn
                }
            } else {
                null
            }
        }
    }
}
