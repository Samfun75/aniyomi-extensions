package io.github.samfun75.gradle.internal.extensions

import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.DependencyHandlerScope

@JvmName("compileOnlyBundle")
internal fun DependencyHandlerScope.compileOnly(dependencyNotation: Provider<ExternalModuleDependencyBundle>) {
    add("compileOnly", dependencyNotation)
}

internal fun DependencyHandlerScope.compileOnly(dependencyNotation: Provider<MinimalExternalModuleDependency>) {
    add("compileOnly", dependencyNotation)
}

@JvmName("implementationBundle")
internal fun DependencyHandlerScope.implementation(dependencyNotation: Provider<ExternalModuleDependencyBundle>) {
    add("implementation", dependencyNotation)
}

internal fun DependencyHandlerScope.implementation(dependencyNotation: ProjectDependency) {
    add("implementation", dependencyNotation)
}
