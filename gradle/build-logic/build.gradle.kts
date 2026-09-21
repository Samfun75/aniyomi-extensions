plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.samWithReceiver)
    alias(libs.plugins.spotless)
    `java-gradle-plugin`
}

// Configuration should be synced with [/gradle/build-logic/src/main/kotlin/SpotlessPlugin.kt]
val ktlintVersion = libs.ktlint.bom.get().version
val editorConfigFile = rootProject.file("../../.editorconfig")
spotless {
    kotlin {
        target("src/**/*.kt", "*.kts")
        ktlint(ktlintVersion)
            .setEditorConfigPath(editorConfigFile)
            .editorConfigOverride(mapOf("max_line_length" to 2147483647))
        trimTrailingWhitespace()
        endWithNewline()
    }
}

dependencies {
    compileOnly(gradleKotlinDsl())
    compileOnly(libs.gradle.agp)
    compileOnly(libs.gradle.kotlin)
    implementation(libs.gradle.spotless)
    implementation(libs.gradle.tapmoc)

    // These allow us to reference the dependency catalogs inside our compiled plugins
    compileOnly(files(libs::class.java.superclass.protectionDomain.codeSource.location))
    compileOnly(files(proj::class.java.superclass.protectionDomain.codeSource.location))
}

samWithReceiver {
    annotation("org.gradle.api.HasImplicitReceiver")
}

gradlePlugin {
    plugins {
        register("android-base") {
            id = proj.plugins.android.base.get().pluginId
            implementationClass = "AndroidBasePlugin"
        }
        register("extension") {
            id = proj.plugins.extension.get().pluginId
            implementationClass = "ExtensionPlugin"
        }
        register("kotlin-library") {
            id = proj.plugins.kotlin.library.get().pluginId
            implementationClass = "KotlinLibraryPlugin"
        }
        register("library") {
            id = proj.plugins.library.get().pluginId
            implementationClass = "LibraryPlugin"
        }
        register("spotless") {
            id = proj.plugins.spotless.get().pluginId
            implementationClass = "SpotlessPlugin"
        }
        register("theme") {
            id = proj.plugins.theme.get().pluginId
            implementationClass = "ThemePlugin"
        }
    }
}
