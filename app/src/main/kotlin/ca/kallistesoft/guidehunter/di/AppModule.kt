package ca.kallistesoft.guidehunter.di

import android.annotation.SuppressLint
import android.content.Context
import ca.kallistesoft.guidehunter.BuildConfig
import ca.kallistesoft.guidehunter.core.ads.AdsConfig
import ca.kallistesoft.guidehunter.core.auth.GoogleSignInConfig
import ca.kallistesoft.guidehunter.core.data.di.DataConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /**
     * The rewarded and native ad units: Google's test units except in release builds (app/build.gradle.kts). Ads are off when the
     * app runs under tests (Robolectric, instrumented): they test the app, and the consent form would cover it.
     */
    @Provides
    fun adsConfig() = AdsConfig(
        rewardedUnitId = BuildConfig.ADMOB_REWARDED_ID,
        nativeUnitId = BuildConfig.ADMOB_NATIVE_ID,
        enabled = !underInstrumentation(),
    )

    @Provides
    fun dataConfig() = DataConfig(applicationsEmail = BuildConfig.APPLICATIONS_EMAIL)

    /**
     * Google sign-in's web client id, which the google-services plugin generates from google-services.json. Looked
     * up by name: without Firebase (a fresh clone, pull requests) the resource doesn't exist and guides can't sign in.
     */
    @Provides
    @SuppressLint("DiscouragedApi")
    fun googleSignInConfig(@ApplicationContext context: Context): GoogleSignInConfig {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return GoogleSignInConfig(webClientId = if (id == 0) null else context.getString(id))
    }

    /** AndroidX Test is loaded into the app's process only when a test instruments it. */
    private fun underInstrumentation(): Boolean =
        runCatching { Class.forName("androidx.test.platform.app.InstrumentationRegistry") }.isSuccess
}
