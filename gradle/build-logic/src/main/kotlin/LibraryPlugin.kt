import com.android.build.api.dsl.LibraryExtension
import io.github.samfun75.gradle.internal.extensions.alias
import io.github.samfun75.gradle.internal.extensions.compileOnly
import io.github.samfun75.gradle.internal.extensions.libs
import io.github.samfun75.gradle.internal.extensions.plugins
import io.github.samfun75.gradle.internal.extensions.proj
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

@Suppress("UNUSED")
class LibraryPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.android.library)
            alias(libs.plugins.kotlin.serialization)

            alias(proj.plugins.android.base)
            alias(proj.plugins.spotless)
        }

        android {
            namespace = "eu.kanade.tachiyomi.lib.${name.replace("-", "")}"
        }

        dependencies {
            compileOnly(libs.bundles.common)
        }
    }
}

private fun Project.android(block: LibraryExtension.() -> Unit) {
    extensions.configure(block)
}
