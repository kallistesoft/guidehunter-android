plugins {
    alias(libs.plugins.guidehunter.android.library)
    alias(libs.plugins.guidehunter.hilt)
}

android {
    namespace = "ca.kallistesoft.guidehunter.core.data"
}

// Guides, and guides' profiles and offers, in Cloud Firestore; guides sign in with Firebase Auth; the user's location from
// Google Play services; the app's settings on the phone in a Preferences DataStore.
dependencies {
    api(project(":core:model"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.location)

    testImplementation(project(":core:testing"))
    testImplementation(libs.mockk)
}
