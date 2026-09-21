plugins {
    alias(proj.plugins.theme)
}

theme {
    baseVersionCode = 3
}

dependencies {
    api(projects.lib.megacloudExtractor)
    api(projects.lib.streamtapeExtractor)
}
