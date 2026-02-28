package com.jimbroze.kbus.contracts.annotations.index

enum class DependencyType {
    PROPERTY,
    FUNCTIONAL,
    COMMAND,
    NON_DEPENDENCY,
}

enum class HandlerType {
    COMMAND,
    QUERY,
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class KbusIndex(
    val dependencies: Array<DependencyInfo>,
    val handlers: Array<HandlerInfo>,
)

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

@Target()
@Retention(AnnotationRetention.BINARY)
annotation class HandlerInfo(
    val handlerType: HandlerType,
    val handlerClass: String,
    val messageClass: String,
    val returnType: String,
    val topLevelDependencies: Array<String>,
)
