package com.jimbroze.kbus.generation

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.jimbroze.kbus.core.MessageHandler

class ContainerGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val busPackageName: String,
    private val loaderInterfaceName: String,
    private val loaderClassName: String,
) {
    fun generateLoaderInterface(packagePath: String, dependencies: Set<NestedDependency>) {
        logger.info("Generating dependency loader interface")

        val fileText = StringBuilder()
        fileText.appendLine("package $packagePath")
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

    fun generateLoaderClass(interfaces: Set<String>, overrides: Set<NestedDependency>): String {
        logger.info("Generating dependency loader abstract class")

        val interfacesString = interfaces.joinToString(", ", prefix = " : ")

        val fileText = StringBuilder()
        fileText.appendLine("package $busPackageName")
        fileText.appendLine()

        fileText.appendLine("abstract class $loaderClassName$interfacesString {")

        for (override in overrides) {
            val dependencyDeclaration = override.declaration
            if (dependencyDeclaration is KSClassDeclaration && !override.isRoot) {
                val string =
                    "override " +
                        generateLoaderValOverride(override, dependencyDeclaration).toString()
                fileText.appendLine(string.prependIndent())
            }
        }

        fileText.appendLine("}")

        val file = codeGenerator.createNewFile(Dependencies(true), busPackageName, loaderClassName)
        file.write(fileText.toString().toByteArray())
        file.close()

        return "$busPackageName.$loaderClassName"
    }

    private fun generateLoaderValOverride(
        dependency: NestedDependency,
        dependencyDeclaration: KSClassDeclaration,
    ): StringBuilder {
        val dependencyName = dependency.name
        val dependencyTypeWithArgs = dependency.getTypeWithArgs()
        val loaderMethodCode = StringBuilder()

        val dependencyConstructorParams = constructorParams(dependencyDeclaration)
        val dependencyTypeWithoutArgs = dependencyDeclaration.qualifiedName!!.asString()

        if (isSingleton(dependency)) {
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

    private fun constructorParams(dependencyDeclaration: KSClassDeclaration): String {
        val dependencyConstructorParamNames =
            dependencyDeclaration.primaryConstructor
                ?.parameters
                ?.map { param -> Dependency.fromParameter(param, useParamName = false).name }
                .orEmpty()

        return dependencyConstructorParamNames.joinToString(", ") { "this.$it" }
    }

    private fun generateLoaderVal(dependency: NestedDependency): String {
        val dependencyName = dependency.name
        val dependencyTypeWithArgs = dependency.getTypeWithArgs()

        return "val $dependencyName: $dependencyTypeWithArgs"
    }

    private fun isSingleton(dependency: NestedDependency): Boolean {
        return !(dependency.declaration is KSClassDeclaration &&
            dependency.declaration.superTypes.any {
                it.resolve().declaration.qualifiedName?.asString() ==
                    MessageHandler::class.qualifiedName
            })
    }
}
