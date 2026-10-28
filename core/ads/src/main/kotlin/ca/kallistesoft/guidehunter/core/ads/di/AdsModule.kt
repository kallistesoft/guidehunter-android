package ca.kallistesoft.guidehunter.core.ads.di

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import ca.kallistesoft.guidehunter.core.ads.AdConsent
import ca.kallistesoft.guidehunter.core.ads.AdsConfig
import ca.kallistesoft.guidehunter.core.ads.AdsManager
import ca.kallistesoft.guidehunter.core.ads.BoostAds
import ca.kallistesoft.guidehunter.core.ads.GoogleAdsSdk
import ca.kallistesoft.guidehunter.core.ads.NativeAds
import ca.kallistesoft.guidehunter.core.ads.ResumedActivity
import ca.kallistesoft.guidehunter.core.ads.RewardedAds
import ca.kallistesoft.guidehunter.core.ads.UmpConsent
import com.google.android.ump.UserMessagingPlatform
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** AdMob behind [RewardedAds], [NativeAds] and [AdConsent]; the app provides [AdsConfig] (the ad unit of its build type). */
@Module
@InstallIn(SingletonComponent::class)
internal object AdsModule {
    @Provides
    @Singleton
    fun adsManager(@ApplicationContext context: Context, config: AdsConfig): AdsManager {
        // Created when the activity is injected, before it resumes: the first resumed activity is seen.
        val resumed = ResumedActivity()
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(resumed)
        return AdsManager(
            consent = UmpConsent(UserMessagingPlatform.getConsentInformation(context)),
            sdk = GoogleAdsSdk(context),
            currentActivity = resumed::get,
            config = config,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            clock = SystemClock::elapsedRealtime,
            isOnline = { online(context) },
        )
    }

    /** A network that reaches the internet (validated), as far as Android knows. */
    fun online(context: Context): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    @Provides
    fun rewardedAds(manager: AdsManager): RewardedAds = manager

    @Provides
    @BoostAds
    fun boostAds(manager: AdsManager): RewardedAds = manager.boost

    @Provides
    fun nativeAds(manager: AdsManager): NativeAds = manager

    @Provides
    fun adConsent(manager: AdsManager): AdConsent = manager
}
