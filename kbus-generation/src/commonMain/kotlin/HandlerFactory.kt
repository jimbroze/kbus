package com.jimbroze.kbus.generation

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeReference
import com.jimbroze.kbus.core.Command
import com.jimbroze.kbus.core.CommandDependencies
import com.jimbroze.kbus.core.CommandHandler
import com.jimbroze.kbus.core.Message
import com.jimbroze.kbus.core.MessageHandler
import com.jimbroze.kbus.core.Query
import com.jimbroze.kbus.core.QueryHandler
import com.jimbroze.kbus.generation.FunctionalDependencyMetadata.DependencyConstructorParameters
import kotlin.reflect.KClass

// TODO names for same declaration with different type args
sealed interface DependencyMetadata {
    val name: String
        get() = typeRef.declaration.simpleName.asString().replaceFirstChar { it.lowercase() }

    val typeRef: KSType
    val prefix: String
        get() = ""

    val accessReference: String
        get() = "$prefix$name"
}

data class PropertyDependencyMetadata(override val typeRef: KSType) : DependencyMetadata

data class FunctionalDependencyMetadata(
    override val typeRef: KSType,
    private val requiresCommandDependencies: Boolean,
) : DependencyMetadata {
    data class DependencyConstructorParameters(val name: String, val typeRef: KClass<*>)

    val functionParameters: List<DependencyConstructorParameters>
        get() =
            if (requiresCommandDependencies)
                listOf(
                    DependencyConstructorParameters(
                        "commandDependencies",
                        CommandDependencies::class,
                    )
                )
            else emptyList()

    override val accessReference: String
        get() {
            val constructorArgNames = this.functionParameters.joinToString(", ") { it.name }
            return "$prefix$name($constructorArgNames)"
        }
}

data class CommandDependencyMetadata(override val typeRef: KSType) : DependencyMetadata {
    override val prefix = "commandDependencies."
}

data class NonDependencyMetadata(override val typeRef: KSType) : DependencyMetadata {
    override val prefix
        get() = error("This dependency should not be used: $typeRef")
}

interface HasChildren {
    val topLevelDependencies: List<DependencyMetadata>
}

// TODO combine with dependency classes/interfaces?
data class HandlerData(
    val nameAsDependency: String,
    val handlerClass: KSClassDeclaration,
    val messageClass: KSClassDeclaration,
    val returnType: KSTypeReference,
    override val topLevelDependencies: List<DependencyMetadata>,
) : HasChildren

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

    val functionParameters: List<DependencyConstructorParameters>
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

    override val functionParameters: List<DependencyConstructorParameters>
        get() =
            listOf(
                DependencyConstructorParameters("commandDependencies", CommandDependencies::class)
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

    override val functionParameters: List<DependencyConstructorParameters>
        get() = emptyList()
}

class HandlerFactory(private val logger: KSPLogger, val dependencyFactory: DependencyFactory) {
    fun createHandler(
        handlerClass: KSClassDeclaration,
        constructorDependencies: List<DependencyMetadata>,
    ): HandlerDefinition? {
        // FIXME Query is interface
        val handlerBaseClassReference = findBaseClass(handlerClass) ?: return null

        val baseClassTypeArgs: List<KSTypeArgument> =
            handlerBaseClassReference.element!!.typeArguments
        if (baseClassTypeArgs.size < 2) {
            val baseClassName =
                handlerBaseClassReference.resolve().declaration.qualifiedName?.asString()
                    ?: handlerBaseClassReference.toString()
            error(
                "Handler base class must have at least two type arguments (message and return type) " +
                    "handler: ${handlerClass.qualifiedName?.asString() ?: handlerClass.simpleName.asString()}; " +
                    "found ${baseClassTypeArgs.size} on base $baseClassName"
            )
        }

        val messageClass =
            baseClassTypeArgs[0].type?.resolve()?.declaration as? KSClassDeclaration
                ?: error("Message type argument is missing or invalid")

        val returnType =
            baseClassTypeArgs[1].type ?: error("Return type argument is missing or invalid")

        return HandlerDefinition.create(
            handlerBaseClassReference.resolve().declaration as KSClassDeclaration,
            HandlerData(
                dependencyFactory.nameForDependency(handlerClass),
                handlerClass,
                messageClass,
                returnType,
                constructorDependencies,
            ),
        )
    }
}

fun findBaseClass(classDeclaration: KSClassDeclaration): KSTypeReference? {
    return classDeclaration.superTypes.firstOrNull {
        (it.resolve().declaration as? KSClassDeclaration)!!.classKind == ClassKind.CLASS
    }
}
