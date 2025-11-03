plugins {
    `java-library`
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

versionCatalogs
    .named("libs")
    .findLibrary("kotlin-stdlib")
    .ifPresent { stdlib ->
        dependencies {
            compileOnly(stdlib)
        }
    }
