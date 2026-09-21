import io.github.samfun75.gradle.internal.configurations.configureKotlin
import io.github.samfun75.gradle.internal.extensions.alias
import io.github.samfun75.gradle.internal.extensions.compileOnly
import io.github.samfun75.gradle.internal.extensions.libs
import io.github.samfun75.gradle.internal.extensions.plugins
import io.github.samfun75.gradle.internal.extensions.proj
import io.github.samfun75.gradle.internal.extensions.spotlessTaskName
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

@Suppress("UNUSED")
class KotlinLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            apply("java-library")
            alias(libs.plugins.kotlin.jvm)

            alias(proj.plugins.spotless)
        }

        configureKotlin()

        dependencies {
            compileOnly(libs.kotlin.stdlib)
        }

        tasks.named("compileKotlin") { dependsOn(spotlessTaskName()) }
    }
}
