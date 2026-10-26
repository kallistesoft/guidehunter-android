plugins {
    alias(libs.plugins.guidehunter.android.library)
    alias(libs.plugins.guidehunter.hilt)
}

android {
    namespace = "ca.kallistesoft.guidehunter.core.data"
}

// Guides and guide applications in Cloud Firestore; the user's location from Google Play services.
dependencies {
    api(project(":core:model"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.location)

    testImplementation(project(":core:testing"))
    testImplementation(libs.mockk)
}
