package com.jimbroze.kbus.generation

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration

class ContainerGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val busPackageName: String,
    private val loaderInterfaceName: String,
    private val loaderClassName: String,
) {
    fun generateLoaderInterface(dependencies: Set<LoaderDependency>) {
        logger.info("Generating dependency loader interface")

        val fileText = StringBuilder()
        fileText.appendLine("package $busPackageName")
        fileText.appendLine()

        fileText.appendLine("interface $loaderInterfaceName {")

        for (dependency in dependencies) {
            fileText.appendLine(generateLoaderVal(dependency).prependIndent())
        }

        fileText.appendLine("}")

        val file =
            codeGenerator.createNewFile(Dependencies(true), busPackageName, loaderInterfaceName)
        file.write(fileText.toString().toByteArray())
        file.close()
    }

    fun generateLoaderClass(dependencies: Set<LoaderDependency>) {
        logger.info("Generating dependency loader abstract class")

        val fileText = StringBuilder()
        fileText.appendLine("package $busPackageName")
        fileText.appendLine()

        fileText.appendLine("abstract class $loaderClassName : $loaderInterfaceName {")

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

        val file = codeGenerator.createNewFile(Dependencies(true), busPackageName, loaderClassName)
        file.write(fileText.toString().toByteArray())
        file.close()
    }

    private fun generateLoaderValOverride(
        dependency: LoaderDependency,
        dependencyDeclaration: KSClassDeclaration,
    ): StringBuilder {
        val dependencyName = dependency.definition.name
        val dependencyTypeWithArgs = dependency.definition.getTypeWithArgs()
        val loaderMethodCode = StringBuilder()

        // fiXME constructor params are actually further (loader) dependencies.
        val dependencyConstructorParams = constructorParams(dependencyDeclaration)
        val dependencyTypeWithoutArgs = dependencyDeclaration.qualifiedName!!.asString()

        if (dependency.isSingleton) {
            loaderMethodCode.appendLine("val $dependencyName: $dependencyTypeWithArgs by lazy {")
            loaderMethodCode.appendLine(
                "    $dependencyTypeWithoutArgs($dependencyConstructorParams)"
            )
            loaderMethodCode.appendLine("}")
        } else {
            loaderMethodCode.appendLine("val $dependencyName: $dependencyTypeWithArgs")
            loaderMethodCode.appendLine(
                "    get() = $dependencyTypeWithoutArgs($dependencyConstructorParams)"
            )
        }

        return loaderMethodCode
    }

    // FIXME name is default
    private fun constructorParams(dependencyDeclaration: KSClassDeclaration): String {
        val dependencyConstructorParamNames =
            dependencyDeclaration.primaryConstructor
                ?.parameters
                ?.map { param ->
                    DependencyDefinition.fromParameter(param, useParamName = false).name
                }
                .orEmpty()

        return dependencyConstructorParamNames.joinToString(", ") { "this.$it" }
    }

    private fun generateLoaderVal(dependency: LoaderDependency): String {
        val dependencyName = dependency.definition.name
        val dependencyTypeWithArgs = dependency.definition.getTypeWithArgs()

        return "val $dependencyName: $dependencyTypeWithArgs"
    }
}
