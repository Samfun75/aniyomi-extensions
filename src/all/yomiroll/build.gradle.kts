plugins {
    alias(proj.plugins.extension)
}

extension {
    name = "Yomiroll"
    versionCode = 5
}

dependencies {
    implementation(libs.ktvine)
}
