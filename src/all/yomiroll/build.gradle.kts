plugins {
    alias(proj.plugins.extension)
}

extension {
    name = "Yomiroll"
    versionCode = 4
}

dependencies {
    implementation(libs.ktvine)
}
