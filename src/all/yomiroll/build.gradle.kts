plugins {
    alias(proj.plugins.extension)
}

extension {
    name = "Yomiroll"
    versionCode = 8
}

dependencies {
    implementation(libs.ktvine)
}
