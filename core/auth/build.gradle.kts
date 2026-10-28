plugins {
    alias(libs.plugins.guidehunter.android.library)
    alias(libs.plugins.guidehunter.hilt)
}

android {
    namespace = "ca.kallistesoft.guidehunter.core.auth"
    testOptions.unitTests.isIncludeAndroidResources = true
}

// Google sign-in (Credential Manager), turned into a Firebase account by :core:data; the account's actions shared by
// the welcome and the settings.
dependencies {
    api(project(":core:data"))
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(project(":core:testing"))
    testImplementation(libs.mockk)
}
