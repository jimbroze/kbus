package com.jimbroze.kbus.generation.processing.dependencies

data class DependencyWithChildren(
    val metadata: Dependency,
    val topLevelDependencies: List<Dependency>,
    val isRoot: Boolean,
)
