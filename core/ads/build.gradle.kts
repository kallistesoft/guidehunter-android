plugins {
    alias(libs.plugins.guidehunter.android.library)
    alias(libs.plugins.guidehunter.hilt)
}

android {
    namespace = "ca.kallistesoft.guidehunter.core.ads"
}

// AdMob: the Google Mobile Ads SDK and Google's consent SDK (UMP), kept behind this module's interfaces.
dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.ads)
    implementation(libs.ump)

    testImplementation(project(":core:testing"))
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}
