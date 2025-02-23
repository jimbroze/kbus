package com.jimbroze.kbus.generation

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability
import kotlin.reflect.KClass
import kotlinx.datetime.Clock

data class DependencyDefinition(
    val declaration: KSDeclaration,
    val typeArgs: List<KSTypeArgument>,
    val isSingleton: Boolean = true,
    val customName: String? = null,
    val nullability: Nullability = Nullability.NOT_NULL,
) {
    companion object {
        fun fromParameter(
            parameter: KSValueParameter,
            paramType: KSType?,
            useParamName: Boolean = false,
        ): DependencyDefinition {
            val type = paramType ?: parameter.type.resolve()
            val typeArgs = parameter.type.element?.typeArguments.orEmpty()

            val customName = if (useParamName) parameter.name?.asString() else null

            return DependencyDefinition(
                type.declaration,
                typeArgs,
                customName = customName,
                nullability = type.nullability,
            )
        }
    }

    fun getName(): String {
        return customName ?: declaration.simpleName.asString().replaceFirstChar { it.lowercase() }
    }

    fun getTypeWithArgs(): String {
        val typeName = StringBuilder(declaration.qualifiedName!!.asString())

        for (typeArg in typeArgs) {
            val type = typeArg.type?.resolve()
            val typeText = type?.declaration?.qualifiedName?.asString() ?: continue
            val variance = typeArg.variance.label
            val nullability = if (type.nullability == Nullability.NULLABLE) "?" else ""

            typeName.append("<$variance $typeText $nullability>")
        }
        if (nullability == Nullability.NULLABLE) typeName.append("?")

        return typeName.toString()
    }
}

data class LoaderDependency(val definition: DependencyDefinition, val isRoot: Boolean)

// DependencyFactory?
class DependencyProcessor(private val busPackageName: String, private val logger: KSPLogger) {
    fun generate(loadedMessages: Set<LoadedHandlerDefinition>): MutableSet<LoaderDependency> {
        val allDependencies = mutableSetOf<LoaderDependency>()

        for (loadedMessageDefinition in loadedMessages) {
            extractedDependenciesOrNull(
                    loadedMessageDefinition.handlerDefinition.handler.primaryConstructor!!
                        .parameters
                )
                ?.let { allDependencies.addAll(it) }
                ?: logger.error("Message handler is not a valid dependency")

            allDependencies.add(
                LoaderDependency(
                    DependencyDefinition(
                        loadedMessageDefinition.handlerDefinition.handler,
                        emptyList(),
                        false,
                    ),
                    false,
                )
            )
        }

        return allDependencies
    }

    private fun extractedDependenciesOrNull(
        parameterDependencies: List<KSValueParameter>
    ): MutableSet<LoaderDependency>? {
        val allDependencies = mutableSetOf<LoaderDependency>()
        for (dependency in parameterDependencies) {
            val type = dependency.type.resolve()
            val depDeclaration = type.declaration
            val dependencyDefinition = DependencyDefinition.fromParameter(dependency, type)

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
                allDependencies.add(LoaderDependency(dependencyDefinition, true))
            } else {
                allDependencies.addAll(nestedDependencies)
                allDependencies.add(LoaderDependency(dependencyDefinition, false))
            }
        }

        return allDependencies
    }
}

class DependencyLoaderGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val busPackageName: String,
) {
    companion object {
        const val LOADER_CLASS_NAME = "GeneratedDIContainer"
    }

    fun generateLoaderClassCode(dependencies: Set<LoaderDependency>) {
        logger.info("Generating dependency loader")

        val fileText = StringBuilder()
        fileText.appendLine("package $busPackageName")
        fileText.appendLine()

        fileText.appendLine("abstract class GeneratedDIContainer {")

        for (dependency in dependencies) {
            fileText.append(generateLoaderMethodCode(dependency))
        }

        fileText.appendLine("}")

        val file =
            codeGenerator.createNewFile(Dependencies(true), busPackageName, LOADER_CLASS_NAME)
        file.write(fileText.toString().toByteArray())
        file.close()
    }

    private fun generateLoaderMethodCode(dependency: LoaderDependency): StringBuilder {
        val declaration = dependency.definition.declaration
        val dependencyName = dependency.definition.getName()
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
                    "${if (firstParam) "" else ", "}this.$parameterName"
                )
                firstParam = false
            }

            val dependencyTypeWithoutArgs =
                dependency.definition.declaration.qualifiedName!!.asString()

            if (dependency.definition.isSingleton) {
                loaderMethodCode.appendLine(
                    "    val $dependencyName: $dependencyTypeWithArgs by lazy {"
                )
                loaderMethodCode.appendLine(
                    "        $dependencyTypeWithoutArgs($handlerDependenciesString)"
                )
                loaderMethodCode.appendLine("    }")
            } else {
                loaderMethodCode.appendLine("    val $dependencyName: $dependencyTypeWithArgs")
                loaderMethodCode.appendLine(
                    "        get() = $dependencyTypeWithoutArgs($handlerDependenciesString)"
                )
            }
        } else {
            loaderMethodCode.appendLine("    abstract val $dependencyName: $dependencyTypeWithArgs")
        }

        return loaderMethodCode
    }
}
