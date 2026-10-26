import java.util.Properties

plugins {
    alias(libs.plugins.guidehunter.android.application)
    alias(libs.plugins.guidehunter.android.compose)
    alias(libs.plugins.guidehunter.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase (the guides and the "Become a guide" emails) is set up only when the project's config file is present, so
// the app also builds without it (pull requests, a fresh clone): it then shows sample guides (README, "Firebase").
if (listOf("", "src/debug/", "src/release/").any { file("${it}google-services.json").exists() }) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Settings that aren't committed: local.properties (each developer) or environment variables (CI).
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun setting(key: String): String? = (localProperties.getProperty(key) ?: System.getenv(key))?.takeIf { it.isNotBlank() }

// Release signing comes from keystore.properties (local, never committed) or environment variables (CI).
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun signingValue(key: String, env: String): String? =
    (keystoreProperties.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

// AdMob IDs. Public identifiers, not secrets. Debug always uses Google's sample IDs (test ads only); release uses
// ADMOB_APP_ID and ADMOB_REWARDED_ID once the AdMob app exists (FOLLOW-UP.md), Google's sample IDs until then.
val admobTest = mapOf(
    "appId" to "ca-app-pub-3940256099942544~3347511713",
    "rewarded" to "ca-app-pub-3940256099942544/5224354917",
)
val admobRelease = mapOf(
    "appId" to (setting("ADMOB_APP_ID") ?: admobTest.getValue("appId")),
    "rewarded" to (setting("ADMOB_REWARDED_ID") ?: admobTest.getValue("rewarded")),
)

/** The AdMob App ID goes in the manifest (`${admobAppId}`), the ad unit in BuildConfig. */
fun com.android.build.api.dsl.ApplicationBuildType.admob(ids: Map<String, String>) {
    manifestPlaceholders["admobAppId"] = ids.getValue("appId")
    buildConfigField("String", "ADMOB_REWARDED_ID", "\"${ids.getValue("rewarded")}\"")
}

android {
    namespace = "ca.kallistesoft.guidehunter"

    defaultConfig {
        applicationId = "ca.kallistesoft.guidehunter"
        // Every Play upload needs a higher versionCode; CI can pass -PversionCode=<n>.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Google Maps SDK for Android key (README, "Google Maps"). Without one the maps stay blank.
        manifestPlaceholders["mapsApiKey"] = setting("MAPS_API_KEY").orEmpty()
        // Who the "Become a guide" emails go to; firestore.rules allows only this address.
        buildConfigField("String", "APPLICATIONS_EMAIL", "\"${setting("APPLICATIONS_EMAIL") ?: "contact@kallistesoft.ca"}\"")
    }

    signingConfigs {
        val storeFile = signingValue("storeFile", "GH_KEYSTORE_FILE")
        if (storeFile != null) {
            create("release") {
                this.storeFile = rootProject.file(storeFile)
                storePassword = signingValue("storePassword", "GH_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "GH_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "GH_KEY_PASSWORD")
            }
        }
    }

    // Code that differs by build type lives in src/debug and src/release (never `if (BuildConfig.DEBUG)`).
    buildTypes {
        debug {
            // Installs next to the Play version, with its own name.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            admob(admobTest)
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Unsigned when no keystore is configured (e.g. pull requests); Play App Signing re-signs uploads.
            signingConfig = signingConfigs.findByName("release")
            admob(admobRelease)
        }
    }

    testOptions {
        // Robolectric tests (src/test) run the real app with its resources.
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core:ads"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":feature:becomeguide"))
    implementation(project(":feature:map"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":core:testing"))
    testImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
