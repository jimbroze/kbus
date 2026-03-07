package com.jimbroze.kbus.generation.processing.handlers

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.generation.processing.dependencies.FunctionalDependency
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName

sealed interface HandlerDefinition {
    val processorMethodName: String
    val messageProcessorName: String
    val handlerData: HandlerData
    val handlerBaseClass: ClassName
    val messageBaseClass: ClassName

    val functionParameters: List<FunctionalDependency.DependencyConstructorParameters>
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
            val commandDependency =
                handlerData.topLevelDependencies.firstOrNull { it.requiresCommandDependencies }
            if (commandDependency !== null) {
                logger.error(
                    "Query handlers cannot have command dependencies. " +
                        "${handlerData.handlerClass.simpleName} contains $commandDependency",
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
