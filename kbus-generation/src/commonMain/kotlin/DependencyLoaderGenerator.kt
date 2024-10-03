package com.jimbroze.kbus.generation

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.jimbroze.kbus.core.MessageBus

object DependencyLoaderGenerator {
    fun generateDependencyLoader(codeGenerator: CodeGenerator, visitorContext: LoadedMessageCode) {
        val packageName = MessageBus::class.qualifiedName!!.split(".").dropLast(1).joinToString(".")

        val fileText =
            generateLoaderCode(
                packageName,
                visitorContext.handlerDependencies,
                visitorContext.loaderMethods,
                visitorContext.busMethods,
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

    fun addMethodToLoaderClass(classDefinition: LoadedHandlerDefinition): StringBuilder {
        val loaderMethodCode = StringBuilder()

        val handlerDependencies =
            classDefinition.handlerDefinition.handler.primaryConstructor!!.parameters.map {
                getParamNames(it)
            }
        val handlerDependenciesString = StringBuilder()
        var firstParam = true
        for (dependency in handlerDependencies) {
            val dependencyName = dependency.name.replaceFirstChar { it.uppercase() }
            handlerDependenciesString.append(
                "${if (firstParam) "" else ", "}this.dependencies.get$dependencyName()"
            )
            firstParam = false
        }
        val handlerName = classDefinition.handlerDefinition.handler.simpleName.asString()
        val handlerType = classDefinition.handlerDefinition.handler.qualifiedName!!.asString()

        loaderMethodCode.appendLine("    fun get$handlerName(): $handlerType {")
        loaderMethodCode.appendLine("        return $handlerType($handlerDependenciesString)")
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
