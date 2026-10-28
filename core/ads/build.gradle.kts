plugins {
    alias(libs.plugins.guidehunter.android.library)
    alias(libs.plugins.guidehunter.android.compose)
    alias(libs.plugins.guidehunter.hilt)
}

android {
    namespace = "ca.kallistesoft.guidehunter.core.ads"
    testOptions.unitTests.isIncludeAndroidResources = true
}

// AdMob: the Google Mobile Ads SDK and Google's consent SDK (UMP), kept behind this module's interfaces, and the
// native ad card (Compose, styled by the design system).
dependencies {
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.ads)
    implementation(libs.ump)

    testImplementation(project(":core:testing"))
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
