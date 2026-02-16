package com.jimbroze.kbus.annotations

enum class DependencyType {
    PROPERTY,
    FUNCTIONAL,
    COMMAND,
    NON_DEPENDENCY,
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class DependencyIndex(val dependencies: Array<DependencyInfo>)

@Target()
@Retention(AnnotationRetention.BINARY)
annotation class DependencyInfo(
    val dependencyType: DependencyType,
    val signature: String,
    val name: String,
    val cannotBeAutoloaded: Boolean,
    val requiresCommandDependencies: Boolean,
    val topLevelDependencies: Array<String>,
)
