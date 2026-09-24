plugins {
    alias(proj.plugins.extension)
}

extension {
    name = "Yomiroll"
    versionCode = 6
}

dependencies {
    implementation(libs.ktvine)
}
