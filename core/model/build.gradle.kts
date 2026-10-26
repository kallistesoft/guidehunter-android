import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin (no Android), so it can move to Kotlin Multiplatform when the iOS app shares it.
plugins {
    alias(libs.plugins.guidehunter.jvm.library)
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}
