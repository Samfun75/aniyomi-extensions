import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.gradle.tasks.PackageAndroidArtifact
import io.github.samfun75.gradle.api.ContentWarning
import io.github.samfun75.gradle.api.dsl.AnimeExtension
import io.github.samfun75.gradle.api.dsl.AnimeTheme
import io.github.samfun75.gradle.internal.extensions.alias
import io.github.samfun75.gradle.internal.extensions.compileOnly
import io.github.samfun75.gradle.internal.extensions.implementation
import io.github.samfun75.gradle.internal.extensions.libs
import io.github.samfun75.gradle.internal.extensions.plugins
import io.github.samfun75.gradle.internal.extensions.proj
import io.github.samfun75.gradle.tasks.GenerateManifestTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

@Suppress("UNUSED")
class ExtensionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.android.application)
            alias(libs.plugins.kotlin.serialization)

            alias(proj.plugins.android.base)
            alias(proj.plugins.spotless)
        }

        val extension = extensions.create<AnimeExtension>("extension").apply {
            contentWarning.convention(ContentWarning.SAFE)
            versionId.convention(1)
            overrideVersionCode.convention(0)
        }
        val applicationIdSuffix = "${project.parent?.name}.${project.name}"
        val extensionLib = proj.versions.ext.lib.get()

        // Themed extensions take their version code from the theme, which is only known once
        // this project's build file has declared which theme it uses.
        val versionCode = objects.property<Int>()
        val versionName = versionCode.map { "$extensionLib.$it" }

        android {
            namespace = "eu.kanade.tachiyomi.animeextension"

            defaultConfig {
                this.applicationIdSuffix = applicationIdSuffix
            }

            sourceSets {
                named("main") {
                    manifest.srcFile(rootProject.file("common/AndroidManifest.xml"))
                    java.directories.apply {
                        clear()
                        add("src")
                    }
                    kotlin.directories.apply {
                        clear()
                        add("src")
                    }
                    res.directories.apply {
                        clear()
                        add("res")
                    }
                    assets.directories.apply {
                        clear()
                        add("assets")
                    }
                }
            }

            lint {
                checkReleaseBuilds = false
            }

            signingConfigs {
                create("release") {
                    storeFile = rootProject.file("signingkey.jks")
                    storePassword = providers.environmentVariable("KEY_STORE_PASSWORD").orNull
                    keyAlias = providers.environmentVariable("ALIAS").orNull
                    keyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
                }
            }

            buildTypes {
                named("release") {
                    signingConfig = if (rootProject.file("signingkey.jks").exists()) {
                        signingConfigs.getByName("release")
                    } else {
                        signingConfigs.getByName("debug")
                    }
                    isMinifyEnabled = true
                    proguardFiles(rootProject.file("common/proguard-rules.pro"))
                    @Suppress("UnstableApiUsage")
                    vcsInfo.include = false
                }
            }

            dependenciesInfo {
                includeInApk = false
            }

            buildFeatures {
                buildConfig = true
            }

            packaging {
                resources.excludes.add("kotlin-tooling-metadata.json")
            }
        }

        val manifestTask = tasks.register<GenerateManifestTask>("generateExtensionManifest") {
            extensionName.set(extension.name)
            qualifiedName.set(extension.qname.orElse(extension.name))
            contentWarning.set(extension.contentWarning)
            this.extensionLib.set(extensionLib)
            versionId.set(extension.versionId)
            sourceNames.set(extension.sourceNames.zip(extension.name) { names, n -> names.ifEmpty { listOf(n) } })
            outputFile.set(layout.buildDirectory.file("generated/extension/AndroidManifest.xml"))
        }

        androidComponents {
            onVariants { variant ->
                variant.sources.manifests.addGeneratedManifestFile(manifestTask) { it.outputFile }

                variant.outputs.forEach { output ->
                    output.versionCode.set(versionCode.map { extensionLib.toInt() * 1000 + it })
                    output.versionName.set(versionName)
                }
            }
        }

        base {
            archivesName.set(versionName.map { "aniyomi-$applicationIdSuffix-v$it" })
        }

        dependencies {
            implementation(project(":core"))
            compileOnly(libs.bundles.common)
        }

        afterEvaluate {
            val themeName = extension.themePackage.orNull
            versionCode.set(
                if (themeName == null) {
                    extension.versionCode.get()
                } else {
                    val theme = project(":lib-multisrc:$themeName")
                    evaluationDependsOn(theme.path)
                    theme.extensions.getByType<AnimeTheme>().baseVersionCode.get() +
                        extension.overrideVersionCode.get()
                },
            )
            versionCode.finalizeValue()

            if (themeName != null) {
                dependencies {
                    implementation(project(":lib-multisrc:$themeName"))
                }

                val baseUrl = extension.baseUrl.orNull
                if (!baseUrl.isNullOrEmpty()) {
                    val (scheme, rest) = baseUrl.split("://", limit = 2)
                    android {
                        defaultConfig.manifestPlaceholders += mapOf(
                            "SOURCESCHEME" to scheme,
                            "SOURCEHOST" to rest.substringBefore("/"),
                        )
                    }
                }
            }

            tasks.withType<PackageAndroidArtifact>().configureEach {
                createdBy.set("")
                doFirst {
                    appMetadata.asFile.orNull?.writeText("")
                }
            }
        }
    }
}

private fun Project.android(block: ApplicationExtension.() -> Unit) {
    extensions.configure(block)
}

private fun Project.androidComponents(block: ApplicationAndroidComponentsExtension.() -> Unit) {
    extensions.configure(block)
}

private fun Project.base(block: BasePluginExtension.() -> Unit) {
    extensions.configure(block)
}
