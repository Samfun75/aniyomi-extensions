plugins {
    alias(proj.plugins.library)
}

dependencies {
    implementation(projects.lib.cryptoaes)
    implementation(projects.lib.playlistUtils)
}
