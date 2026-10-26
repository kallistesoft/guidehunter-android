plugins {
    alias(libs.plugins.guidehunter.android.library)
    alias(libs.plugins.guidehunter.android.compose)
}

android {
    namespace = "ca.kallistesoft.guidehunter.core.designsystem"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
