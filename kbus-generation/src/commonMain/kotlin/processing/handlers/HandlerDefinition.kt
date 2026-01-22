package com.jimbroze.kbus.generation.processing.handlers

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.jimbroze.kbus.core.common.Message
import com.jimbroze.kbus.core.common.MessageHandler
import com.jimbroze.kbus.core.messages.command.Command
import com.jimbroze.kbus.core.messages.command.CommandHandler
import com.jimbroze.kbus.core.messages.query.Query
import com.jimbroze.kbus.core.messages.query.QueryHandler
import com.jimbroze.kbus.core.uow.CommandDependencies
import com.jimbroze.kbus.generation.processing.dependencies.FunctionalDependency
import kotlin.reflect.KClass

sealed interface HandlerDefinition {
    val processorMethodName: String
    val messageProcessorName: String
    val handlerData: HandlerData
    val handlerBaseClass: KClass<out MessageHandler<*>>
    val messageBaseClass: KClass<out Message>

    companion object {
        fun create(
            handlerBaseClass: KSClassDeclaration,
            handlerData: HandlerData,
        ): HandlerDefinition? {
            return when (handlerBaseClass.qualifiedName!!.asString()) {
                CommandHandler::class.qualifiedName -> CommandHandlerDefinition(handlerData)
                QueryHandler::class.qualifiedName -> QueryHandlerDefinition(handlerData)
                else -> null
            }
        }
    }

    val functionParameters: List<FunctionalDependency.DependencyConstructorParameters>
}

data class CommandHandlerDefinition(override val handlerData: HandlerData) : HandlerDefinition {
    override val handlerBaseClass
        get() = CommandHandler::class

    override val messageBaseClass
        get() = Command::class

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

// TODO is error() and require() ok or better UX for errors? Use Logger?
data class QueryHandlerDefinition(override val handlerData: HandlerData) : HandlerDefinition {
    init {
        val commandDependencyOrNull =
            handlerData.topLevelDependencies.firstOrNull { it.requiresCommandDependencies }
        require(commandDependencyOrNull == null) {
            "Query handlers cannot have command dependencies. " +
                "${handlerData.handlerClass.simpleName} contains $commandDependencyOrNull"
        }
    }

    override val handlerBaseClass
        get() = QueryHandler::class

    override val messageBaseClass: KClass<out Message>
        get() = Query::class

    override val processorMethodName: String
        get() = "fetch"

    override val messageProcessorName: String
        get() = "queryFetcher"

    override val functionParameters: List<FunctionalDependency.DependencyConstructorParameters>
        get() = emptyList()
}
