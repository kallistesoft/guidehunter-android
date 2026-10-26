plugins {
    alias(libs.plugins.guidehunter.android.feature)
}

android {
    namespace = "ca.kallistesoft.guidehunter.feature.becomeguide"
}

dependencies {
    implementation(libs.maps.compose)
}
