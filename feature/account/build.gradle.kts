plugins {
    alias(libs.plugins.guidehunter.android.feature)
}

android {
    namespace = "ca.kallistesoft.guidehunter.feature.account"
}

// The welcome (sign in or later, then user or guide) and the settings, both modes.
dependencies {
    implementation(project(":core:ads"))
    implementation(project(":core:auth"))
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.mockk)
}
