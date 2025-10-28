package com.jimbroze.kbus.generation

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.jimbroze.kbus.core.CommandDependencies
import com.jimbroze.kbus.core.MessageHandler

class ContainerGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val loaderInterfaceName: String,
    private val combinedContainerInterfaceName: String,
    private val loaderClassName: String,
) {
    fun generateLoaderInterface(packagePath: String, dependencies: Set<NestedDependency>) {
        logger.info("Generating dependency loader interface")

        val fileText = StringBuilder()
        fileText.appendLine("package $packagePath")
        fileText.appendLine()

        fileText.appendLine("interface $loaderInterfaceName {")

        for (dependency in dependencies) {
            fileText.appendLine(generateAbstractDependency(dependency).prependIndent())
        }

        fileText.appendLine("}")

        val file = codeGenerator.createNewFile(Dependencies(true), packagePath, loaderInterfaceName)
        file.write(fileText.toString().toByteArray())
        file.close()
    }

    fun generateCombinedContainerInterface(
        packagePath: String,
        interfaceClassNames: Set<KSName>,
    ): String {
        logger.info("Generating combined dependency loader interface")

        val interfacesString =
            interfaceClassNames.joinToString(", ", prefix = " : ", transform = { it.asString() })

        val fileText = StringBuilder()
        fileText.appendLine("package $packagePath")
        fileText.appendLine()

        fileText.appendLine("interface $combinedContainerInterfaceName$interfacesString")

        val file =
            codeGenerator.createNewFile(
                Dependencies(true),
                packagePath,
                combinedContainerInterfaceName,
            )
        file.write(fileText.toString().toByteArray())
        file.close()

        return "$packagePath.$combinedContainerInterfaceName"
    }

    fun generateLoaderClass(
        packagePath: String,
        dependencies: Set<NestedDependency>,
        commandDependenciesProps: CommandDependencyProperties,
    ) {
        logger.info("Generating dependency loader abstract class")

        val fileText = StringBuilder()
        fileText.appendLine("package $packagePath")
        fileText.appendLine()

        fileText.appendLine("abstract class $loaderClassName : $combinedContainerInterfaceName {")

        val allDependencies = dependencies + commandDependenciesProps.asDependencies()
        for (dependency in dependencies) {
            val dependencyIsNotRoot =
                dependency.declaration is KSClassDeclaration && !dependency.isRoot
            if (dependencyIsNotRoot) {
                // TODO move override?
                val string =
                    "override " +
                        generateLoaderValOverride(
                                dependency,
                                dependency.declaration,
                                allDependencies,
                                commandDependenciesProps,
                            )
                            .toString()
                fileText.appendLine(string.prependIndent())
            }
        }

        fileText.appendLine("}")

        val file = codeGenerator.createNewFile(Dependencies(true), packagePath, loaderClassName)
        file.write(fileText.toString().toByteArray())
        file.close()
    }

    @Suppress("ReturnCount")
    private fun generateLoaderValOverride(
        dependency: NestedDependency,
        dependencyDeclaration: KSClassDeclaration,
        allDependencies: Set<NestedDependency>,
        commandDependenciesProps: CommandDependencyProperties,
    ): StringBuilder {
        val dependencyName = dependency.name
        val dependencyTypeWithArgs = dependency.getTypeWithArgs()

        val dependencyConstructorParams =
            constructorParams(dependency, dependencyDeclaration, allDependencies)
        val dependencyTypeWithoutArgs = dependencyDeclaration.qualifiedName!!.asString()

        // If require command deps, use function. If not, use val
        if (shouldBeFunctional(dependency)) {
            val functionConstructorParamNames =
                dependencyConstructorParams.map {
                    if (commandDependenciesProps.contains(it.declaration)) {
                        "commandDependencies.${it.name}"
                    } else {
                        if (it.isCommandDependency) {
                            "${it.name}(commandDependencies)"
                        } else {
                            it.name
                        }
                    }
                }
            val paramNames = combineParameterNames(functionConstructorParamNames)
            val loaderMethodCode = StringBuilder()
            loaderMethodCode.appendLine(
                generateAbstractFunctionDependency(dependencyName, dependencyTypeWithArgs)
            )
            loaderMethodCode.appendLine("    = $dependencyTypeWithoutArgs($paramNames)")
            return loaderMethodCode
        } else if (isSingleton(dependency)) {
            val paramNames = combineParameterNames(dependencyConstructorParams.map { it.name })
            val loaderMethodCode = StringBuilder()
            loaderMethodCode.appendLine("val $dependencyName: $dependencyTypeWithArgs by lazy {")
            loaderMethodCode.appendLine("    $dependencyTypeWithoutArgs($paramNames)")
            loaderMethodCode.appendLine("}")
            return loaderMethodCode
        } else {
            val paramNames = combineParameterNames(dependencyConstructorParams.map { it.name })
            val loaderMethodCode = StringBuilder()
            loaderMethodCode.appendLine(
                generateAbstractPropertyDependency(dependencyName, dependencyTypeWithArgs)
            )
            loaderMethodCode.appendLine("    get() = $dependencyTypeWithoutArgs($paramNames)")
            return loaderMethodCode
        }
    }

    private fun constructorParams(
        dependency: NestedDependency,
        dependencyDeclaration: KSClassDeclaration,
        allDependencies: Set<NestedDependency>,
    ): List<NestedDependency> {
        val orEmpty =
            dependencyDeclaration.primaryConstructor
                ?.parameters
                ?.mapIndexedNotNull { idx, _ ->
                    allDependencies.find { it.name == dependency.childNames[idx] }
                }
                .orEmpty()
        return orEmpty
    }

    private fun combineParameterNames(dependencies: List<String>): String {
        return dependencies
            .map { it }
            .joinToString(", ") { it.takeIf { it.startsWith("commandDependencies") } ?: "this.$it" }
    }

    private fun generateAbstractDependency(dependency: NestedDependency): String {
        val dependencyName = dependency.name
        val dependencyTypeWithArgs = dependency.getTypeWithArgs()

        return if (shouldBeFunctional(dependency)) {
            generateAbstractFunctionDependency(dependencyName, dependencyTypeWithArgs)
        } else {
            generateAbstractPropertyDependency(dependencyName, dependencyTypeWithArgs)
        }
    }

    private fun generateAbstractPropertyDependency(
        dependencyName: String,
        dependencyTypeWithArgs: String,
    ): String = "val $dependencyName: $dependencyTypeWithArgs"

    private fun generateAbstractFunctionDependency(
        dependencyName: String,
        dependencyTypeWithArgs: String,
    ): String {
        val commandDependenciesType = CommandDependencies::class.qualifiedName!!
        return "fun $dependencyName(commandDependencies: $commandDependenciesType): $dependencyTypeWithArgs"
    }

    // TODO check this?
    private fun isSingleton(dependency: NestedDependency): Boolean {
        return !(dependency.declaration is KSClassDeclaration &&
            dependency.declaration.superTypes.any {
                it.resolve().declaration.qualifiedName?.asString() ==
                    MessageHandler::class.qualifiedName
            })
    }

    private fun shouldBeFunctional(dependency: NestedDependency): Boolean {
        return dependency.isCommandDependency
    }
}
