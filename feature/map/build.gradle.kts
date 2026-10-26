plugins {
    alias(libs.plugins.guidehunter.android.feature)
}

android {
    namespace = "ca.kallistesoft.guidehunter.feature.map"
}

dependencies {
    implementation(project(":core:ads"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.maps.compose)

    testImplementation(libs.mockk)
}
