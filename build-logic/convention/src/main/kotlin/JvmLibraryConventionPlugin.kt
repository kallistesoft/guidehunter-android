import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure

/**
 * Pure Kotlin module (no Android): models and app logic, tested on the JVM. The module sets
 * `kotlin.compilerOptions.jvmTarget` to match, since build-logic doesn't compile against the Kotlin Gradle plugin.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("guidehunter.kover")
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }
            addUnitTestDependencies()
        }
    }
}
