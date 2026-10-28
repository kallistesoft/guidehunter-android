plugins {
    alias(libs.plugins.guidehunter.android.library)
    alias(libs.plugins.guidehunter.android.compose)
}

android {
    namespace = "ca.kallistesoft.guidehunter.core.testing"
}

// Shared test doubles and rules (testImplementation in the modules that use them).
dependencies {
    api(project(":core:ads"))
    api(project(":core:auth"))
    api(project(":core:data"))
    api(project(":core:designsystem"))
    api(libs.androidx.compose.ui.test.junit4)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.robolectric)
    api(libs.turbine)
}
