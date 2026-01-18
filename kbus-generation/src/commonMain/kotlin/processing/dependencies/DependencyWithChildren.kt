package com.jimbroze.kbus.generation.processing.dependencies

// TODO add constructorArgs method and make handlerData implement this?
data class DependencyWithChildren(
    val metadata: Dependency,
    val topLevelDependencies: List<Dependency>,
    val isRoot: Boolean,
)
