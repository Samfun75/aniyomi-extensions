plugins {
    alias(proj.plugins.extension)
}

extension {
    name = "Yomiroll"
    versionCode = 2
}

dependencies {
    implementation(libs.ktvine)
}
