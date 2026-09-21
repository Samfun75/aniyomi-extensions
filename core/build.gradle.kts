plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)

    alias(proj.plugins.android.base)
    alias(proj.plugins.spotless)
}

android {
    namespace = "eu.kanade.tachiyomi.lib.core"

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    compileOnly(libs.bundles.common)
}
