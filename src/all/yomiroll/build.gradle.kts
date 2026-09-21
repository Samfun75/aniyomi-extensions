plugins {
    alias(proj.plugins.extension)
}

extension {
    name = "Yomiroll"
    versionCode = 3
}

dependencies {
    implementation(libs.ktvine)
}
