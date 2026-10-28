// Plugins are declared here once so every module loads them from the same class loader.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.guidehunter.kover)
}

// Unit test coverage of the app's modules, merged (see KoverConventionPlugin): ./gradlew koverVerifyUnit.
// Not :core:testing (test doubles) nor :baselineprofile (a device test that records the profile).
dependencies {
    listOf(
        ":app",
        ":core:ads",
        ":core:auth",
        ":core:data",
        ":core:designsystem",
        ":core:model",
        ":core:ui",
        ":feature:account",
        ":feature:becomeguide",
        ":feature:map",
    ).forEach { kover(project(it)) }
}
