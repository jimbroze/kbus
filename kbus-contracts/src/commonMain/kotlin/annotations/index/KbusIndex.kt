package com.jimbroze.kbus.contracts.annotations.index

enum class DependencyType {
    PROPERTY,
    FUNCTIONAL,
    COMMAND,
    CONTEXT_COMMANDS,
    NON_DEPENDENCY,
}

enum class HandlerType {
    COMMAND,
    QUERY,
    DOMAIN_EVENT,
    INTEGRATION_EVENT,
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class KbusIndex(
    val dependencies: Array<DependencyInfo>,
    val handlers: Array<HandlerInfo>,
    val autoPublishEvents: Array<AutoPublishInfo> = [],
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
    /**
     * The bounded context this handler belongs to, stamped by the producing module's KSP run from
     * the `kbus.boundedContextIdentity` build arg. `""` means unassigned — folded into the default
     * context, and deliberately distinct from a context literally named "default".
     */
    val module: String = "",
)

@Target()
@Retention(AnnotationRetention.BINARY)
annotation class AutoPublishInfo(val integrationEventClass: String, val domainEventClass: String)
