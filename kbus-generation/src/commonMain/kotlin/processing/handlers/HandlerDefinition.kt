package com.jimbroze.kbus.generation.processing.handlers

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.jimbroze.kbus.annotations.HandlerType
import com.jimbroze.kbus.core.messages.command.Command
import com.jimbroze.kbus.core.messages.command.CommandHandler
import com.jimbroze.kbus.core.messages.query.Query
import com.jimbroze.kbus.core.messages.query.QueryHandler
import com.jimbroze.kbus.core.uow.CommandDependencies
import com.jimbroze.kbus.generation.processing.dependencies.FunctionalDependency
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName

sealed interface HandlerDefinition {
    val processorMethodName: String
    val messageProcessorName: String
    val handlerData: HandlerData
    val handlerBaseClass: ClassName
    val messageBaseClass: ClassName

    companion object {
        fun create(
            handlerBaseClass: KSClassDeclaration,
            handlerData: HandlerData,
            logger: KSPLogger,
        ): HandlerDefinition? {
            return when (handlerBaseClass.qualifiedName!!.asString()) {
                CommandHandler::class.qualifiedName -> CommandHandlerDefinition(handlerData)
                QueryHandler::class.qualifiedName ->
                    createNonCommand(QueryHandlerDefinition(handlerData), logger, handlerBaseClass)
                else -> null
            }
        }

        fun createFromType(
            typeOfHandler: HandlerType,
            handlerData: HandlerData,
            logger: KSPLogger,
        ): HandlerDefinition {
            return when (typeOfHandler) {
                HandlerType.COMMAND -> CommandHandlerDefinition(handlerData)
                HandlerType.QUERY ->
                    createNonCommand(QueryHandlerDefinition(handlerData), logger, null)
            }
        }

        fun createNonCommand(
            handlerDefinition: HandlerDefinition,
            logger: KSPLogger,
            handlerClass: KSClassDeclaration?,
        ): HandlerDefinition {
            val commandDependency =
                handlerDefinition.handlerData.topLevelDependencies.firstOrNull {
                    it.requiresCommandDependencies
                }
            if (commandDependency !== null) {
                logger.error(
                    "Query handlers cannot have command dependencies. " +
                        "${handlerDefinition.handlerData.handlerClass.simpleName} contains $commandDependency",
                    handlerClass,
                )
            }

            return handlerDefinition
        }
    }

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

// FIXME use logger for user-related errors. Error/require otherwise
data class QueryHandlerDefinition(override val handlerData: HandlerData) : HandlerDefinition {
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
}
