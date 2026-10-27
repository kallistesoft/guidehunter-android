plugins {
    alias(libs.plugins.guidehunter.android.feature)
}

android {
    namespace = "ca.kallistesoft.guidehunter.feature.becomeguide"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    // Google sign-in for guides (Credential Manager), turned into a Firebase account by :core:data.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.maps.compose)

    testImplementation(libs.mockk)
}
