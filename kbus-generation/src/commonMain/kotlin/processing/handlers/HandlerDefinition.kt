package com.jimbroze.kbus.generation.processing.handlers

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.jimbroze.kbus.contracts.annotations.index.DependencyBundle
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.generation.processing.dependencies.FunctionalDependency
import com.jimbroze.kbus.generation.processing.dependencies.parameterName
import com.jimbroze.kbus.generation.processing.dependencies.parameterType
import com.jimbroze.kbus.generation.processing.dependencies.widestWith
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName

sealed interface HandlerDefinition {
    val processorMethodName: String
    val messageProcessorName: String
    val handlerData: HandlerData
    val handlerBaseClass: ClassName
    val messageBaseClass: ClassName

    val functionParameters: List<FunctionalDependency.DependencyConstructorParameters>

    /** The narrowest bundle this handler's own dependencies need. */
    val requiredBundle: DependencyBundle
        get() =
            handlerData.topLevelDependencies.fold(DependencyBundle.NONE) { widest, dependency ->
                widest.widestWith(dependency.requiredBundle)
            }

    /**
     * What its accessor is given, which its dependencies are then read from. Wider than
     * [requiredBundle] where the handler kind fixes it: every command handler is built from a
     * command's own dependencies whether or not it asks for anything out of them.
     */
    val suppliedBundle: DependencyBundle
        get() = requiredBundle
}

data class CommandHandlerDefinition(override val handlerData: HandlerData) : HandlerDefinition {
    override val handlerBaseClass
        get() = CommandHandler::class.asClassName()

    override val messageBaseClass
        get() = Command::class.asClassName()

    override val processorMethodName: String
        get() = "execute"

    override val messageProcessorName: String
        get() = "commandExecutor"

    override val suppliedBundle
        get() = DependencyBundle.COMMAND

    override val functionParameters: List<FunctionalDependency.DependencyConstructorParameters>
        get() =
            listOf(
                FunctionalDependency.DependencyConstructorParameters(
                    "commandDependencies",
                    CommandDependencies::class,
                )
            )
}

class QueryHandlerDefinition private constructor(override val handlerData: HandlerData) :
    HandlerDefinition {
    companion object {
        fun create(
            handlerData: HandlerData,
            logger: KSPLogger,
            handlerClass: KSClassDeclaration?,
        ): HandlerDefinition? {
            val invocationScopedDependency =
                handlerData.topLevelDependencies.firstOrNull {
                    it.requiredBundle != DependencyBundle.NONE
                }
            if (invocationScopedDependency !== null) {
                logger.error(
                    "Query handlers cannot have command dependencies. " +
                        "${handlerData.handlerClass.simpleName} contains $invocationScopedDependency",
                    handlerClass,
                )
                return null
            }

            return QueryHandlerDefinition(handlerData)
        }
    }

    override val handlerBaseClass
        get() = QueryHandler::class.asClassName()

    override val messageBaseClass
        get() = Query::class.asClassName()

    override val processorMethodName: String
        get() = "fetch"

    override val messageProcessorName: String
        get() = "queryFetcher"

    override val functionParameters: List<FunctionalDependency.DependencyConstructorParameters>
        get() = emptyList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QueryHandlerDefinition

        return handlerData == other.handlerData
    }

    override fun hashCode(): Int {
        return handlerData.hashCode()
    }
}

enum class EventHandlerKind {
    DOMAIN,
    INTEGRATION,
}

data class EventHandlerDefinition(
    override val handlerData: HandlerData,
    val kind: EventHandlerKind,
) : HandlerDefinition {
    override val handlerBaseClass
        get() = EventHandler::class.asClassName()

    override val messageBaseClass
        get() = Event::class.asClassName()

    override val processorMethodName: String
        get() = "dispatch"

    override val messageProcessorName: String
        get() = "eventDispatcher"

    override val functionParameters: List<FunctionalDependency.DependencyConstructorParameters>
        get() =
            if (suppliedBundle == DependencyBundle.NONE) emptyList()
            else
                listOf(
                    FunctionalDependency.DependencyConstructorParameters(
                        suppliedBundle.parameterName,
                        suppliedBundle.parameterType,
                    )
                )
}
