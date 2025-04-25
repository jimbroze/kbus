package com.jimbroze.kbus.generation

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability
import kotlin.collections.orEmpty
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
    fun generateFrom(type: KSType, includeNested: Boolean): Set<LoaderDependency> {
        return extractedDependenciesOrNull(type, includeNested = includeNested) ?: emptySet()
    }

    fun generateFrom(
        properties: Sequence<KSPropertyDeclaration>,
        includeNested: Boolean,
    ): Set<LoaderDependency> {
        // TODO also go through functions?

        return properties
            .flatMap { prop ->
                extractedDependenciesOrNull(
                    dependency = prop.type.resolve(),
                    customName = prop.simpleName.asString(),
                    typeArgs = prop.type.element?.typeArguments.orEmpty(),
                    includeNested,
                ) ?: emptySet()
            }
            .toSet()
    }

    private fun nestedDependenciesOrNull(
        classDeclaration: KSClassDeclaration
    ): MutableSet<LoaderDependency>? {
        val allDependencies = mutableSetOf<LoaderDependency>()

        for (dependency in classDeclaration.primaryConstructor?.parameters.orEmpty()) {
            extractedDependenciesOrNull(
                    dependency.type.resolve(),
                    typeArgs = dependency.type.element?.typeArguments.orEmpty(),
                )
                ?.let { allDependencies.addAll(it) } ?: return null
        }

        return allDependencies
    }

    private fun extractedDependenciesOrNull(
        dependency: KSType,
        customName: String? = null,
        typeArgs: List<KSTypeArgument> = emptyList(),
        includeNested: Boolean = true,
    ): Set<LoaderDependency>? {
        val allDependencies = mutableSetOf<LoaderDependency>()

        val depDeclaration = dependency.declaration

        val dependencyDefinition =
            DependencyDefinition(
                depDeclaration,
                typeArgs,
                customName = customName,
                nullability = dependency.nullability,
            )

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

        // TODO prevent calculating nested if extractNested is false? Probably can't do this? At
        // least prevent recursion?
        val nestedDependencies =
            if (isNestedDependency) {
                nestedDependenciesOrNull(depDeclaration)
            } else {
                null
            }

        if (nestedDependencies === null) {
            allDependencies.add(LoaderDependency(dependencyDefinition, true))
        } else {
            if (includeNested) allDependencies.addAll(nestedDependencies)
            allDependencies.add(LoaderDependency(dependencyDefinition, false))
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
        const val LOADER_INTERFACE_NAME = "IGeneratedDIContainer"
        const val LOADER_CLASS_NAME = "AbstractGeneratedDIContainer"
    }

    fun generateLoaderInterface(dependencies: Set<LoaderDependency>) {
        logger.info("Generating dependency loader interface")

        val fileText = StringBuilder()
        fileText.appendLine("package $busPackageName")
        fileText.appendLine()

        fileText.appendLine("interface $LOADER_INTERFACE_NAME {")

        for (dependency in dependencies) {
            fileText.appendLine(generateLoaderVal(dependency).prependIndent())
        }

        fileText.appendLine("}")

        val file =
            codeGenerator.createNewFile(Dependencies(true), busPackageName, LOADER_INTERFACE_NAME)
        file.write(fileText.toString().toByteArray())
        file.close()
    }

    fun generateLoaderClass(dependencies: Set<LoaderDependency>) {
        logger.info("Generating dependency loader abstract class")

        val fileText = StringBuilder()
        fileText.appendLine("package $busPackageName")
        fileText.appendLine()

        fileText.appendLine("abstract class $LOADER_CLASS_NAME : $LOADER_INTERFACE_NAME {")

        for (dependency in dependencies) {
            val dependencyDeclaration = dependency.definition.declaration
            if (dependencyDeclaration is KSClassDeclaration && !dependency.isRoot) {
                val string =
                    "override " +
                        generateLoaderValOverride(dependency, dependencyDeclaration).toString()
                fileText.appendLine(string.prependIndent())
            }
        }

        fileText.appendLine("}")

        val file =
            codeGenerator.createNewFile(Dependencies(true), busPackageName, LOADER_CLASS_NAME)
        file.write(fileText.toString().toByteArray())
        file.close()
    }

    private fun generateLoaderValOverride(
        dependency: LoaderDependency,
        dependencyDeclaration: KSClassDeclaration,
    ): StringBuilder {
        val dependencyName = dependency.definition.getName()
        val dependencyTypeWithArgs = dependency.definition.getTypeWithArgs()
        val loaderMethodCode = StringBuilder()
        val dependencyConstructorParams =
            dependencyDeclaration.primaryConstructor
                ?.parameters
                ?.map { DependencyDefinition.fromParameter(it, null) }
                .orEmpty()
        val handlerDependenciesString = StringBuilder()
        var firstParam = true
        for (constructorParam in dependencyConstructorParams) {
            val parameterName = constructorParam.getName().replaceFirstChar { it.lowercase() }
            handlerDependenciesString.append("${if (firstParam) "" else ", "}this.$parameterName")
            firstParam = false
        }

        val dependencyTypeWithoutArgs = dependencyDeclaration.qualifiedName!!.asString()

        if (dependency.definition.isSingleton) {
            loaderMethodCode.appendLine("val $dependencyName: $dependencyTypeWithArgs by lazy {")
            loaderMethodCode.appendLine(
                "    $dependencyTypeWithoutArgs($handlerDependenciesString)"
            )
            loaderMethodCode.appendLine("}")
        } else {
            loaderMethodCode.appendLine("val $dependencyName: $dependencyTypeWithArgs")
            loaderMethodCode.appendLine(
                "    get() = $dependencyTypeWithoutArgs($handlerDependenciesString)"
            )
        }

        return loaderMethodCode
    }

    private fun generateLoaderVal(dependency: LoaderDependency): String {
        val dependencyName = dependency.definition.getName()
        val dependencyTypeWithArgs = dependency.definition.getTypeWithArgs()

        return "val $dependencyName: $dependencyTypeWithArgs"
    }
}
