package ca.kallistesoft.guidehunter.di

import ca.kallistesoft.guidehunter.BuildConfig
import ca.kallistesoft.guidehunter.core.ads.AdsConfig
import ca.kallistesoft.guidehunter.core.data.di.DataConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /**
     * The rewarded ad unit: Google's test unit except in release builds (app/build.gradle.kts). Ads are off when the
     * app runs under tests (Robolectric, instrumented): they test the app, and the consent form would cover it.
     */
    @Provides
    fun adsConfig() = AdsConfig(rewardedUnitId = BuildConfig.ADMOB_REWARDED_ID, enabled = !underInstrumentation())

    @Provides
    fun dataConfig() = DataConfig(applicationsEmail = BuildConfig.APPLICATIONS_EMAIL)

    /** AndroidX Test is loaded into the app's process only when a test instruments it. */
    private fun underInstrumentation(): Boolean =
        runCatching { Class.forName("androidx.test.platform.app.InstrumentationRegistry") }.isSuccess
}
