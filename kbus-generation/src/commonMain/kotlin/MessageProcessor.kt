package com.jimbroze.kbus.generation

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.jimbroze.kbus.core.Command
import com.jimbroze.kbus.annotations.Load
import com.jimbroze.kbus.core.MessageBus
import java.io.OutputStream

fun OutputStream.appendText(str: String) {
    this.write(str.toByteArray())
}

data class DependencyDefinition(
    val name: String,
    val typeName: String,
)
data class CommandClassDefinition(
    val handler: KSClassDeclaration,
    val loadedCommandType: String,
//    val loadedCommandReturnType: String,
)

class MessageProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
): SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver
                .getSymbolsWithAnnotation(Load::class.qualifiedName.toString())
            .filterIsInstance<KSClassDeclaration>()

        if (!symbols.iterator().hasNext()) return emptyList()

        val visitor = CommandClassVisitor()
        val processedMessages = symbols.map { it.accept(visitor, Unit) }

        generateDependencyLoader(processedMessages.toList().filterNotNull())

        val unableToProcess = symbols.filterNot { it.validate() }.toList()
        return unableToProcess
    }

    fun generateDependencyLoader(commandDefinitions: List<CommandClassDefinition>) {
//        TODO change this to combine the dependency strings already created for each handler
        val allHandlerDependencies = commandDefinitions.flatMap { classDefinition ->
            classDefinition.handler.primaryConstructor!!.parameters.map { getParamNames(it) }
        }.distinct()

        val packageName = MessageBus::class.qualifiedName!!.split(".").dropLast(1).joinToString(".")
        val file = codeGenerator.createNewFile(
            Dependencies(true),
            packageName,
            "GeneratedDependencyLoader"
        )
        file.appendText("package $packageName\n\n")

        // Interface
        file.appendText("interface GeneratedDependencies {\n")
        for (dependency in allHandlerDependencies) {
            val dependencyName = dependency.name.replaceFirstChar { it.uppercase() }
            file.appendText("    fun get$dependencyName(): ${dependency.typeName}\n")
        }
        file.appendText("}\n")

        // Loader
        file.appendText(
            "class CompileTimeGeneratedLoader(private val dependencies: GeneratedDependencies) {\n"
        )

        for (commandDefinition in commandDefinitions) {
            val handlerDependencies = commandDefinition.handler.primaryConstructor!!.parameters.map { getParamNames(it) }
            val handlerDependenciesString = StringBuilder()
            var firstParam = true
            for (dependency in handlerDependencies) {
                val dependencyName = dependency.name.replaceFirstChar { it.uppercase() }
                handlerDependenciesString.append("${if (firstParam) "" else ", "}this.dependencies.get$dependencyName()")
                firstParam = false
            }
            val handlerName = commandDefinition.handler.simpleName.asString()
            val handlerType = commandDefinition.handler.qualifiedName!!.asString()

            file.appendText("    fun get$handlerName(): $handlerType {\n")
            file.appendText("        return $handlerType($handlerDependenciesString)\n")
            file.appendText("    }\n")
        }
        file.appendText("}\n")

        // Bus
//        TODO use MessageBus constructor for type safety? Replace pre-written class instead?
        file.appendText("class CompileTimeLoadedMessageBus(\n")
        file.appendText("    middleware: List<Middleware>,\n")
        file.appendText("    private val loader: CompileTimeGeneratedLoader,\n")
        file.appendText(") : MessageBus(middleware) {\n")
        for (commandDefinition in commandDefinitions) {
            val handlerName = commandDefinition.handler.simpleName.asString()
            val loadedCommandType = commandDefinition.loadedCommandType

            file.appendText(
                "    suspend fun execute(loadedCommand: $loadedCommandType)\n" +
                "        = this.execute(loadedCommand.command, this.loader.get$handlerName())\n"
            )
        }
        file.appendText("}\n")

        file.close()
    }


    inner class CommandClassVisitor() : KSDefaultVisitor<Unit, CommandClassDefinition?>() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit): CommandClassDefinition? {
            if (classDeclaration.classKind != ClassKind.CLASS) {
                logger.error("Only classes can be annotated with @${Load::class.simpleName.toString()}", classDeclaration)
                return null
            }

            val handlerClass: KSClassDeclaration = classDeclaration

//            TODO improve finding handle function. Need to get override and check that command handler?
            val handleFunctions = handlerClass.getDeclaredFunctions()
                .filter {
                    it.simpleName.asString() == "handle"
                    && it.parameters.count() == 1
                }

            var commandClass: KSClassDeclaration? = null
            for (ksFunctionDeclaration in handleFunctions) {
                val thisCommandType = ksFunctionDeclaration.parameters.first()
                    .type.resolve().declaration
                if (thisCommandType is KSClassDeclaration && thisCommandType.superTypes.any {
                    it.resolve().declaration.qualifiedName!!.asString() == Command::class.qualifiedName
                }) {
                    commandClass = thisCommandType
                    continue
                }
            }

            if (commandClass == null) {
                logger.error("Message handler must have a valid 'handle' function.", classDeclaration)
                return null
            }

            val packageName = commandClass.containingFile!!.packageName.asString()
            val commandClassName = commandClass.simpleName.asString()
            val handlerClassName = handlerClass.simpleName.asString()
            val loadedCommandClassName = "${commandClassName}Loaded"

            val loadedCommandConstructorParameters = StringBuilder()
            val commandConstructorParameters = StringBuilder()
            var firstParam = true
//            TODO handler null constructor?
            for (parameter in commandClass.primaryConstructor?.parameters!!) {
                val parameterNames = getParamNames(parameter)
                val name = parameterNames.name
                val typeName = parameterNames.typeName

                loadedCommandConstructorParameters.append("${if (firstParam) "" else ", "}$name: $typeName")
                commandConstructorParameters.append("${if (firstParam) "" else ", "}$name")

                firstParam = false
            }

            val file = codeGenerator.createNewFile(
                Dependencies(true, commandClass.containingFile!!),
                packageName,
                loadedCommandClassName
            )
            file.appendText(
                "package $packageName\n\n" +
                "class $loadedCommandClassName($loadedCommandConstructorParameters) {\n" +
                "    val command = ${commandClassName}(${commandConstructorParameters})\n" +
                "    suspend fun handle(handler: $handlerClassName) = handler.handle(command)\n" +
                "}\n"
            )
            file.close()

            return CommandClassDefinition(
                handlerClass,
                "$packageName.$loadedCommandClassName",
            )
        }

        override fun defaultHandler(node: KSNode, data: Unit): CommandClassDefinition? {
            return null
        }
    }

    private fun getParamNames(parameter: KSValueParameter): DependencyDefinition {
        val name = parameter.name!!.asString()
        val typeName = StringBuilder(parameter.type.resolve().declaration.qualifiedName?.asString() ?: "<ERROR>")
        val typeArgs = parameter.type.element!!.typeArguments
        if (parameter.type.element!!.typeArguments.isNotEmpty()) {
            typeName.append("<")
            typeName.append(
                typeArgs.joinToString(", ") {
                    val type = it.type?.resolve()
                    "${it.variance.label} ${type?.declaration?.qualifiedName?.asString() ?: "ERROR"}" +
                            if (type?.nullability == Nullability.NULLABLE) "?" else ""
                }
            )
            typeName.append(">")
        }
        return DependencyDefinition(name, typeName.toString())
    }
}
