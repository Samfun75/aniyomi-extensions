// Build logic ported from https://github.com/Secozzi/aniyomi-extensions (Apache-2.0).

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../libs.versions.toml"))
        }
        create("proj") {
            from(files("../proj.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
