package com.jimbroze.kbus.generation

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.jimbroze.kbus.core.MessageBus

data class LoadedMessageCode(
    val busMethods: StringBuilder = StringBuilder(),
    val loaderMethods: StringBuilder = StringBuilder(),
    val handlerDependencies: MutableSet<ParameterDefinition> = mutableSetOf(),
) {
    fun addMessage(message: LoadedMessageCode) {
        busMethods.append(message.busMethods)
        loaderMethods.append(message.loaderMethods)
        handlerDependencies.addAll(message.handlerDependencies)
    }
}

object DependencyLoaderGenerator {
    fun generateDependencyLoader(
        codeGenerator: CodeGenerator,
        loadedMessages: Set<LoadedHandlerDefinition>,
    ) {
        val packageName = MessageBus::class.qualifiedName!!.split(".").dropLast(1).joinToString(".")

        val loadedMessageCode = LoadedMessageCode()
        for (loadedMessageDefinition in loadedMessages) {
            val handlerDependencies =
                loadedMessageDefinition.handlerDefinition.handler.primaryConstructor!!
                    .parameters
                    .map { getParamNames(it) }
                    .toMutableSet()

            loadedMessageCode.addMessage(
                LoadedMessageCode(
                    addMethodToBusClass(loadedMessageDefinition),
                    addMethodToLoaderClass(loadedMessageDefinition.handlerDefinition.handler),
                    handlerDependencies,
                )
            )
        }

        val fileText =
            generateLoaderCode(
                packageName,
                loadedMessageCode.handlerDependencies,
                loadedMessageCode.loaderMethods,
                loadedMessageCode.busMethods,
            )

        val file =
            codeGenerator.createNewFile(
                Dependencies(true),
                packageName,
                "GeneratedDependencyLoader",
            )
        file.write(fileText.toString().toByteArray())
        file.close()
    }

    private fun generateLoaderCode(
        packageName: String,
        handlerDependencies: Set<ParameterDefinition>,
        loaderMethods: StringBuilder,
        busMethods: StringBuilder,
    ): StringBuilder {
        val fileText = StringBuilder()

        fileText.appendLine("package $packageName")
        fileText.appendLine()
        fileText.append(generateDependenciesInterface(handlerDependencies))
        fileText.append(generateLoaderClasses(loaderMethods))
        fileText.append(generateBusClass(busMethods))

        return fileText
    }

    fun addMethodToBusClass(classDefinition: LoadedHandlerDefinition): StringBuilder {
        val busMethodCode = StringBuilder()

        val messageType = classDefinition.handlerDefinition.messageBaseClass.simpleName!!
        val messageTypeLowercase = messageType.lowercase()

        val handlerName = classDefinition.handlerDefinition.handler.simpleName.asString()
        val loadedMessageName = classDefinition.loadedMessageName

        busMethodCode.appendLine("    suspend fun execute(loaded$messageType: $loadedMessageName)")
        busMethodCode.appendLine(
            "        = this.execute(loaded$messageType.$messageTypeLowercase, this.loader.get$handlerName())"
        )

        return busMethodCode
    }

    private fun generateBusClass(busMethods: StringBuilder): StringBuilder {
        // TODO use MessageBus constructor for type safety? Replace pre-written class instead?

        val busClassCode = StringBuilder()
        busClassCode.appendLine("class CompileTimeLoadedMessageBus(")
        busClassCode.appendLine("    middleware: List<Middleware>,")
        busClassCode.appendLine("    private val loader: CompileTimeGeneratedLoader,")
        busClassCode.appendLine(") : MessageBus(middleware) {")

        busClassCode.append(busMethods)

        busClassCode.appendLine("}")

        return busClassCode
    }

    private fun generateDependenciesInterface(
        allHandlerDependencies: Set<ParameterDefinition>
    ): StringBuilder {
        val dependenciesInterfaceCode = StringBuilder()

        dependenciesInterfaceCode.appendLine("interface GeneratedDependencies {")
        for (dependency in allHandlerDependencies) {
            val dependencyName = dependency.name.replaceFirstChar { it.uppercase() }
            dependenciesInterfaceCode.appendLine(
                "    fun get$dependencyName(): ${dependency.typeName}"
            )
        }
        dependenciesInterfaceCode.appendLine("}")

        return dependenciesInterfaceCode
    }

    private fun addMethodToLoaderClass(typeDefinition: KSDeclaration): StringBuilder {
        val handlerName = typeDefinition.simpleName.asString()
        val handlerType = typeDefinition.qualifiedName!!.asString()

        val implementation =
            if (typeDefinition is KSClassDeclaration) {
                val handlerDependencies =
                    typeDefinition.primaryConstructor!!.parameters.map { getParamNames(it) }
                val handlerDependenciesString = StringBuilder()
                var firstParam = true
                for (dependency in handlerDependencies) {
                    val dependencyName = dependency.name.replaceFirstChar { it.uppercase() }
                    handlerDependenciesString.append(
                        "${if (firstParam) "" else ", "}this.dependencies.get$dependencyName()"
                    )
                    firstParam = false
                }

                "$handlerType($handlerDependenciesString)"
            } else {
                "this.dependencies.get$handlerName()"
            }

        val loaderMethodCode = StringBuilder()
        loaderMethodCode.appendLine("    fun get$handlerName(): $handlerType {")
        loaderMethodCode.appendLine("        return $implementation")
        loaderMethodCode.appendLine("    }")

        return loaderMethodCode
    }

    private fun generateLoaderClasses(loaderMethods: StringBuilder): StringBuilder {
        val stringBuilder = StringBuilder()
        stringBuilder.appendLine(
            "class CompileTimeGeneratedLoader(private val dependencies: GeneratedDependencies) {"
        )

        stringBuilder.append(loaderMethods)

        stringBuilder.appendLine("}")

        return stringBuilder
    }
}
