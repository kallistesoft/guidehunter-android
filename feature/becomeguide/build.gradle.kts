plugins {
    alias(libs.plugins.guidehunter.android.feature)
}

android {
    namespace = "ca.kallistesoft.guidehunter.feature.becomeguide"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.maps.compose)

    testImplementation(libs.mockk)
}
