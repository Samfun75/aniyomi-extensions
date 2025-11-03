plugins {
    id("com.android.library")
    kotlin("android")
    id("kotlinx-serialization")
}

android {
    compileSdk = AndroidConfig.compileSdk

    defaultConfig {
        minSdk = AndroidConfig.minSdk
    }

    namespace = "eu.kanade.tachiyomi.lib.${name.replace("-", "")}"
}

kotlin {
    jvmToolchain(17)
}

versionCatalogs
    .named("libs")
    .findBundle("common")
    .ifPresent { common ->
        dependencies {
            compileOnly(common)
        }
    }
