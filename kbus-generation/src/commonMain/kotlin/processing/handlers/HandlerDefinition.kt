package com.jimbroze.kbus.generation.processing.handlers

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.jimbroze.kbus.core.Command
import com.jimbroze.kbus.core.CommandDependencies
import com.jimbroze.kbus.core.CommandHandler
import com.jimbroze.kbus.core.Message
import com.jimbroze.kbus.core.MessageHandler
import com.jimbroze.kbus.core.Query
import com.jimbroze.kbus.core.QueryHandler
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

data class QueryHandlerDefinition(override val handlerData: HandlerData) : HandlerDefinition {
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
