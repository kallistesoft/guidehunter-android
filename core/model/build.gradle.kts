import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin (no Android), so it can move to Kotlin Multiplatform when the iOS app shares it.
plugins {
    alias(libs.plugins.guidehunter.jvm.library)
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

dependencies {
    // Dates of offers (departures), KMP-ready like the rest of the module.
    api(libs.kotlinx.datetime)
}
