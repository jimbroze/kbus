package com.jimbroze.kbus.generation

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability
import com.jimbroze.kbus.core.MessageBus
import kotlin.reflect.KClass
import kotlinx.datetime.Clock

data class DependencyDefinition(
    val declaration: KSDeclaration,
    val typeArgs: List<KSTypeArgument>,
) {
    companion object {
        fun fromParameter(
            parameter: KSValueParameter,
            paramType: KSDeclaration?,
        ): DependencyDefinition {
            val declaration = paramType ?: parameter.type.resolve().declaration
            val typeArgs = parameter.type.element?.typeArguments.orEmpty()

            return DependencyDefinition(declaration, typeArgs)
        }

        fun fromLoadedMessage(loadedMessage: LoadedHandlerDefinition): DependencyDefinition {
            return DependencyDefinition(loadedMessage.handlerDefinition.handler, emptyList())
        }
    }

    fun getName(): String {
        return declaration.simpleName.asString()
    }

    fun getTypeWithArgs(): String {
        val typeName = StringBuilder(declaration.qualifiedName!!.asString())

        if (typeArgs.isNotEmpty()) {
            typeName.append("<")
            typeName.append(
                typeArgs.joinToString(", ") {
                    val type = it.type?.resolve()
                    // TODO improve this. Will qualified name always be available?
                    "${it.variance.label} ${type?.declaration?.qualifiedName?.asString() ?: "ERROR"}" +
                        if (type?.nullability == Nullability.NULLABLE) "?" else ""
                }
            )
            typeName.append(">")
        }

        return typeName.toString()
    }
}

data class LoaderDependency(val definition: DependencyDefinition, val isRoot: Boolean)

data class AllDependencies(
    val loaderDependencies: MutableSet<LoaderDependency> = mutableSetOf(),
    val busDependencies: MutableSet<LoadedHandlerDefinition> = mutableSetOf(),
) {
    fun addDependency(dependency: DependencyDefinition, isRootDependency: Boolean) {
        loaderDependencies.add(LoaderDependency(dependency, isRootDependency))
    }

    fun addBusMethod(loadedHandler: LoadedHandlerDefinition) {
        busDependencies.add(loadedHandler)
    }

    fun addAll(dependencies: AllDependencies) {
        loaderDependencies.addAll(dependencies.loaderDependencies)
    }
}

// FIXME for functional args use varName and ClassName? e.g
// stringCombinerForTestDuplicateGeneratorCommandHandler

class DependencyLoaderGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) {
    companion object {
        const val LOADER_CLASS_NAME = "GeneratedDIContainer"
    }

    private val busPackageName =
        MessageBus::class.qualifiedName!!.split(".").dropLast(1).joinToString(".")

    fun generate(loadedMessages: Set<LoadedHandlerDefinition>) {
        logger.info("Generating dependency loader")
        val allDependencies = AllDependencies()

        for (loadedMessageDefinition in loadedMessages) {
            extractedDependenciesOrNull(
                    loadedMessageDefinition.handlerDefinition.handler.primaryConstructor!!
                        .parameters
                )
                ?.let { allDependencies.addAll(it) }
                ?: logger.error("Message handler is not a valid dependency")

            allDependencies.addDependency(
                DependencyDefinition.fromLoadedMessage(loadedMessageDefinition),
                false,
            )

            allDependencies.addBusMethod(loadedMessageDefinition)
        }

        val fileText = generateCode(busPackageName, allDependencies)

        val file =
            codeGenerator.createNewFile(Dependencies(true), busPackageName, LOADER_CLASS_NAME)
        file.write(fileText.toString().toByteArray())
        file.close()
    }

    private fun extractedDependenciesOrNull(
        parameterDependencies: List<KSValueParameter>
    ): AllDependencies? {
        val allDependencies = AllDependencies()
        for (dependency in parameterDependencies) {
            val depDeclaration = dependency.type.resolve().declaration
            val dependencyDefinition =
                DependencyDefinition.fromParameter(dependency, depDeclaration)

            val cannotBeRootPackages = listOf("kotlin", "kotlinx.datetime")
            val cannotBeRootExceptions = listOf<KClass<out Any>>(Clock::class)

            if (
                cannotBeRootPackages.contains(depDeclaration.packageName.asString()) &&
                    cannotBeRootExceptions.none() {
                        depDeclaration.qualifiedName!!.asString() == it.qualifiedName
                    }
            ) {
                return null
            }

            val isNestedDependency =
                depDeclaration is KSClassDeclaration &&
                    depDeclaration.primaryConstructor?.parameters.isNullOrEmpty().not() &&
                    depDeclaration.packageName.asString() != busPackageName

            val nestedDependencies =
                if (isNestedDependency) {
                    extractedDependenciesOrNull(
                        depDeclaration.primaryConstructor?.parameters.orEmpty()
                    )
                } else {
                    null
                }

            if (nestedDependencies === null) {
                allDependencies.addDependency(dependencyDefinition, true)
            } else {
                allDependencies.addAll(nestedDependencies)
                allDependencies.addDependency(dependencyDefinition, false)
            }
        }

        return allDependencies
    }

    private fun generateCode(packageName: String, dependencies: AllDependencies): StringBuilder {
        val fileText = StringBuilder()

        fileText.appendLine("package $packageName")
        fileText.appendLine()
        fileText.append(generateLoaderClass(dependencies.loaderDependencies))
        fileText.append(generateBusClass(dependencies.busDependencies))

        return fileText
    }

    private fun generateBusClass(handlers: Set<LoadedHandlerDefinition>): StringBuilder {
        // TODO use MessageBus constructor for type safety? Replace pre-written class instead?

        val busClassCode = StringBuilder()
        busClassCode.appendLine("class CompileTimeLoadedMessageBus(")
        busClassCode.appendLine("    middleware: List<Middleware>,")
        busClassCode.appendLine("    private val loader: $LOADER_CLASS_NAME,")
        busClassCode.appendLine(") : MessageBus(middleware) {")

        for (handler in handlers) {
            busClassCode.append(addMethodToBusClass(handler))
        }

        busClassCode.appendLine("}")

        return busClassCode
    }

    private fun addMethodToBusClass(classDefinition: LoadedHandlerDefinition): StringBuilder {
        val busMethodCode = StringBuilder()

        val messageType = classDefinition.handlerDefinition.messageBaseClass.simpleName!!
        val messageTypeLowercase = messageType.lowercase()

        val handlerName =
            classDefinition.handlerDefinition.handler.simpleName.asString().replaceFirstChar {
                it.lowercase()
            }
        val loadedMessageName = classDefinition.loadedMessageName

        busMethodCode.appendLine("    suspend fun execute(loaded$messageType: $loadedMessageName)")
        busMethodCode.appendLine(
            "        = this.execute(loaded$messageType.$messageTypeLowercase, this.loader.$handlerName())"
        )

        return busMethodCode
    }

    private fun generateLoaderClass(dependencies: Set<LoaderDependency>): StringBuilder {
        val stringBuilder = StringBuilder()
        stringBuilder.appendLine("abstract class GeneratedDIContainer {")

        for (dependency in dependencies) {
            stringBuilder.append(generateLoaderMethod(dependency))
        }

        stringBuilder.appendLine("}")

        return stringBuilder
    }

    private fun generateLoaderMethod(dependency: LoaderDependency): StringBuilder {
        val declaration = dependency.definition.declaration
        val dependencyName = dependency.definition.getName().replaceFirstChar { it.lowercase() }
        val dependencyTypeWithArgs = dependency.definition.getTypeWithArgs()

        val loaderMethodCode = StringBuilder()
        if (declaration is KSClassDeclaration && !dependency.isRoot) {
            val dependencyConstructorParams =
                declaration.primaryConstructor
                    ?.parameters
                    ?.map { DependencyDefinition.fromParameter(it, null) }
                    .orEmpty()
            val handlerDependenciesString = StringBuilder()
            var firstParam = true
            for (constructorParam in dependencyConstructorParams) {
                val parameterName = constructorParam.getName().replaceFirstChar { it.lowercase() }
                handlerDependenciesString.append(
                    "${if (firstParam) "" else ", "}this.$parameterName()"
                )
                firstParam = false
            }

            val dependencyTypeWithoutArgs =
                dependency.definition.declaration.qualifiedName!!.asString()

            loaderMethodCode.appendLine("    fun $dependencyName(): $dependencyTypeWithArgs {")
            loaderMethodCode.appendLine(
                "        return $dependencyTypeWithoutArgs($handlerDependenciesString)"
            )
            loaderMethodCode.appendLine("    }")
        } else {
            loaderMethodCode.appendLine(
                "    abstract fun $dependencyName(): $dependencyTypeWithArgs"
            )
        }

        return loaderMethodCode
    }
}
