package ca.kallistesoft.guidehunter.core.ads.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import ca.kallistesoft.guidehunter.core.ads.AdsConfig
import ca.kallistesoft.guidehunter.core.ads.RewardOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/** The wiring: one manager behind both interfaces, and "online" read from Android's validated network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AdsModuleTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `one manager serves the rewarded ads, the native ads and the consent`() = runTest {
        val manager = AdsModule.adsManager(context, AdsConfig("rewarded-1", "native-1", enabled = false))
        assertSame(manager, AdsModule.rewardedAds(manager))
        assertSame(manager, AdsModule.nativeAds(manager))
        assertNull("ads off: no native ad", manager.load())
        assertSame(manager, AdsModule.adConsent(manager))
        assertEquals("ads off: given at once", RewardOutcome.NO_ADS, manager.showForReward())
    }

    @Test
    fun `online means a validated network`() {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(connectivity).setNetworkCapabilities(connectivity.activeNetwork, capabilities)
        assertFalse(AdsModule.online(context))

        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        assertTrue(AdsModule.online(context))
    }
}
