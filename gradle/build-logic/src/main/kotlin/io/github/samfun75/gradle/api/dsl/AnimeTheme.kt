package io.github.samfun75.gradle.api.dsl

import org.gradle.api.provider.Property

abstract class AnimeTheme {
    abstract val baseVersionCode: Property<Int>
}
