plugins {
    alias(proj.plugins.extension)
}

extension {
    name = "Yomiroll"
    versionCode = 7
}

dependencies {
    implementation(libs.ktvine)
}
