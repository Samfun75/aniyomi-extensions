package io.github.samfun75.gradle.api.dsl

import io.github.samfun75.gradle.api.ContentWarning
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class AnimeExtension {
    abstract val name: Property<String>
    abstract val qname: Property<String>
    abstract val versionCode: Property<Int>
    abstract val versionId: Property<Int>
    abstract val contentWarning: Property<ContentWarning>
    abstract val sourceNames: ListProperty<String>

    /** Name of the [/lib-multisrc] module this extension is built on, if any. */
    abstract val themePackage: Property<String>

    /** Added to the theme's own version code to form this extension's version code. */
    abstract val overrideVersionCode: Property<Int>

    /** Fills the deep link host and scheme placeholders of a theme's manifest. */
    abstract val baseUrl: Property<String>
}
