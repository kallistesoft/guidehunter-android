import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

/** SDK levels shared by every Android module. */
internal object Sdk {
    const val COMPILE = 37
    const val TARGET = 37
    const val MIN = 26
}

internal val JAVA_VERSION = JavaVersion.VERSION_17

/** Unit test libraries every module gets. */
internal fun Project.addUnitTestDependencies() {
    dependencies {
        add("testImplementation", libs.lib("junit"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        add("testImplementation", libs.lib("turbine"))
    }
}
