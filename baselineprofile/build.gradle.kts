import com.android.build.api.dsl.ManagedVirtualDevice

// Records the app's baseline profile (the code paths of a cold start, the map, a guide's offer), so the release
// build ships them precompiled: `./gradlew :app:generateBaselineProfile` on a connected device or the managed
// emulator below, then commit app/src/main/generated/baselineProfiles/. See README, "Baseline profile".
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "ca.kallistesoft.guidehunter.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28 // the macrobenchmark library's minimum
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"

    testOptions.managedDevices.allDevices {
        create<ManagedVirtualDevice>("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp"
        }
    }
}

baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
