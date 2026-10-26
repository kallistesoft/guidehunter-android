import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("guidehunter.kover")
            extensions.configure<LibraryExtension> {
                compileSdk = Sdk.COMPILE
                defaultConfig {
                    minSdk = Sdk.MIN
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = JAVA_VERSION
                    targetCompatibility = JAVA_VERSION
                }
                // Local unit tests touch a few framework stubs (SavedStateHandle, Uri); return defaults instead of throwing.
                testOptions.unitTests.isReturnDefaultValues = true
            }
            addUnitTestDependencies()
        }
    }
}
