plugins {
    alias(libs.plugins.guidehunter.android.library)
    alias(libs.plugins.guidehunter.android.compose)
}

android {
    namespace = "ca.kallistesoft.guidehunter.core.ui"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(project(":core:model"))
    api(project(":core:designsystem"))
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)

    testImplementation(project(":core:testing"))
}
