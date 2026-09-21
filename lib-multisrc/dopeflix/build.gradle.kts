plugins {
    alias(proj.plugins.theme)
}

theme {
    baseVersionCode = 20
}

dependencies {
    api(projects.lib.doodExtractor)
    api(projects.lib.cryptoaes)
    api(projects.lib.playlistUtils)
}
