import com.diffplug.gradle.spotless.SpotlessExtension
import io.github.samfun75.gradle.internal.extensions.alias
import io.github.samfun75.gradle.internal.extensions.libs
import io.github.samfun75.gradle.internal.extensions.plugins
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

@Suppress("UNUSED")
class SpotlessPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.spotless)
        }

        // Configuration should be synced with [/gradle/build-logic/build.gradle.kts]
        val ktlintVersion = libs.ktlint.bom.get().version
        spotless {
            kotlin {
                // The root project's globs reach into every module, including their build directories.
                targetExclude("**/build/**")
                target("src/**/*.kt", "*.kts")
                ktlint(ktlintVersion)
                    .editorConfigOverride(mapOf("max_line_length" to 2147483647))
                trimTrailingWhitespace()
                endWithNewline()
            }

            format("xml") {
                targetExclude("**/build/**")
                target("src/**/*.xml", "AndroidManifest.xml")
                trimTrailingWhitespace()
                endWithNewline()
            }
        }
    }
}

private fun Project.spotless(block: SpotlessExtension.() -> Unit) {
    extensions.configure(block)
}
