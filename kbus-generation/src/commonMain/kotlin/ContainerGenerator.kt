package com.jimbroze.kbus.generation

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.jimbroze.kbus.core.Command
import com.jimbroze.kbus.core.CommandDependencies
import com.jimbroze.kbus.core.CommandHandler
import com.jimbroze.kbus.core.DomainEventMapper
import com.jimbroze.kbus.core.Event
import com.jimbroze.kbus.core.EventHandler
import com.jimbroze.kbus.core.EventMapper
import com.jimbroze.kbus.core.EventMapperProvider
import com.jimbroze.kbus.core.InlineIntegrationEventMapper
import com.jimbroze.kbus.core.IntegrationEventMapper
import com.jimbroze.kbus.core.MessageHandler
import com.jimbroze.kbus.core.MessageHandlerFactoryStore
import com.jimbroze.kbus.core.MessageHandlerLocator
import com.jimbroze.kbus.core.PersistingEventFactory
import com.jimbroze.kbus.core.Query
import com.jimbroze.kbus.core.QueryHandler

// TODO change reflection to use resolver
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

    fun generateHandlerLocator(
        packagePath: String,
        dependencies: Set<NestedDependency>,
        commandDependenciesProps: CommandDependencyProperties,
        locatorClassName: String,
        factoryClassName: String,
    ) {
        val fileText = StringBuilder()
        fileText.appendLine("package $packagePath")
        fileText.appendLine()

        // TODO remove imports?
        fileText.appendLine("import ${Command::class.qualifiedName!!}")
        fileText.appendLine("import ${CommandDependencies::class.qualifiedName!!}")
        fileText.appendLine("import ${CommandHandler::class.qualifiedName!!}")
        fileText.appendLine("import ${DomainEventMapper::class.qualifiedName!!}")
        fileText.appendLine("import ${Event::class.qualifiedName!!}")
        fileText.appendLine("import ${EventHandler::class.qualifiedName!!}")
        fileText.appendLine("import ${EventMapper::class.qualifiedName!!}")
        fileText.appendLine("import ${EventMapperProvider::class.qualifiedName!!}")
        fileText.appendLine("import ${InlineIntegrationEventMapper::class.qualifiedName!!}")
        fileText.appendLine("import ${IntegrationEventMapper::class.qualifiedName!!}")
        fileText.appendLine("import ${"com.jimbroze.kbus.core.KBusResult"}")
        fileText.appendLine("import ${MessageHandlerFactoryStore::class.qualifiedName!!}")
        fileText.appendLine("import ${MessageHandlerLocator::class.qualifiedName!!}")
        fileText.appendLine("import ${PersistingEventFactory::class.qualifiedName!!}")
        fileText.appendLine("import ${Query::class.qualifiedName!!}")
        fileText.appendLine("import ${QueryHandler::class.qualifiedName!!}")
        fileText.appendLine()

        fileText.append(generateLocator(locatorClassName, factoryClassName))
        fileText.append(
            generateHandlerFactory(dependencies, commandDependenciesProps, factoryClassName)
        )

        val file = codeGenerator.createNewFile(Dependencies(true), packagePath, locatorClassName)
        file.write(fileText.toString().toByteArray())
        file.close()
    }

    // FIXME don't generate locator. Pass implementation of HandlerFactory
    private fun generateLocator(locatorClassName: String, factoryClassName: String): StringBuilder {
        logger.info("Generating handler locator class")

        val constructorString = "(dependencies: $combinedContainerInterfaceName)"

        val interfacesString =
            setOf(
                    MessageHandlerLocator::class.qualifiedName!!,
                    EventMapperProvider::class.qualifiedName!!,
                )
                .joinToString(", ", prefix = " : ")

        val fileText = StringBuilder()
        fileText.appendLine("class $locatorClassName$constructorString$interfacesString {")
        fileText.appendLine(
            "internal val generatedHandlerFactory = $factoryClassName(dependencies)".prependIndent()
        )
        fileText.appendLine(
            "private val eventMapper = ${EventMapper::class.simpleName!!}(${PersistingEventFactory::class.simpleName!!}(${MessageHandlerFactoryStore::class.simpleName!!}()))"
                .prependIndent()
        )
        fileText.appendLine(
            "override val domainEventMapper = eventMapper as ${DomainEventMapper::class.simpleName!!}"
                .prependIndent()
        )
        fileText.appendLine(
            "override val integrationEventMapper = eventMapper as ${IntegrationEventMapper::class.simpleName!!}"
                .prependIndent()
        )
        fileText.appendLine(
            "override val inlineIntegrationEventMapper = eventMapper as ${InlineIntegrationEventMapper::class.simpleName!!}"
                .prependIndent()
        )
        fileText.appendLine()

        fileText.appendLine(
            "override fun <TCommand : ${Command::class.simpleName!!}<TResult>, TResult : KBusResult> handlerFor("
                .prependIndent()
        )
        fileText.appendLine("command: TCommand,".prependIndent().prependIndent())
        fileText.appendLine(
            "commandDependencies: ${CommandDependencies::class.simpleName!!},"
                .prependIndent()
                .prependIndent()
        )
        fileText.appendLine(
            "): ${CommandHandler::class.simpleName!!}<TCommand, TResult>? {".prependIndent()
        )
        fileText.appendLine(
            "return generatedHandlerFactory.handlerFor(command, commandDependencies)"
                .prependIndent()
                .prependIndent()
        )
        fileText.appendLine("}".prependIndent())
        fileText.appendLine()

        fileText.appendLine(
            "override fun <TQuery : ${Query::class.simpleName!!}<TResult>, TResult : KBusResult> handlerFor("
                .prependIndent()
        )
        fileText.appendLine("query: TQuery,".prependIndent().prependIndent())
        fileText.appendLine(
            "): ${QueryHandler::class.simpleName!!}<TQuery, TResult>? {".prependIndent()
        )
        fileText.appendLine(
            "return generatedHandlerFactory.handlerFor(query)".prependIndent().prependIndent()
        )
        fileText.appendLine("}".prependIndent())
        fileText.appendLine()

        fileText.appendLine(
            "override fun <TEvent : ${Event::class.simpleName!!}> handlersFor(event: TEvent): List<${EventHandler::class.simpleName!!}<TEvent>> {"
                .prependIndent()
        )
        fileText.appendLine("return eventMapper.handlersFor(event)".prependIndent().prependIndent())
        fileText.appendLine("}".prependIndent())
        fileText.appendLine("}")
        fileText.appendLine()

        return fileText
    }

    private fun generateHandlerFactory(
        dependencies: Set<NestedDependency>,
        commandDependenciesProps: CommandDependencyProperties,
        factoryClassName: String,
    ): StringBuilder {
        val allDependencies = dependencies + commandDependenciesProps.asDependencies()
        // TODO create HandlerAsDependency class
        val commandHandlers = dependencies.filter { it.isCommandHandler() }
        val queryHandlers = dependencies.filter { it.isQueryHandler() }
        val handlers = commandHandlers + queryHandlers

        logger.info("Generating handler locator class")
        val constructorString = "(private val dependencies: $combinedContainerInterfaceName)"

        val fileText = StringBuilder()

        fileText.appendLine("class $factoryClassName$constructorString {")

        fileText.appendLine(
            "fun <TCommand : ${Command::class.simpleName!!}<TResult>, TResult : KBusResult> handlerFor("
                .prependIndent()
        )
        fileText.appendLine("command: TCommand,".prependIndent().prependIndent())
        fileText.appendLine(
            "commandDependencies: ${CommandDependencies::class.simpleName!!},"
                .prependIndent()
                .prependIndent()
        )
        fileText.appendLine(
            "): ${CommandHandler::class.simpleName!!}<TCommand, TResult>? {".prependIndent()
        )
        fileText.appendLine("@Suppress(\"UNCHECKED_CAST\")".prependIndent().prependIndent())
        fileText.appendLine("return when (command) {".prependIndent().prependIndent())
        commandHandlers.forEach { dependency ->
            val handlerClassName =
                messageForHandler(dependency.declaration as KSClassDeclaration)!!
                    .message
                    .qualifiedName!!
                    .asString()
            fileText.appendLine(
                "    is $handlerClassName -> this.${dependency.name}(commandDependencies)"
                    .prependIndent()
                    .prependIndent()
            )
        }
        fileText.appendLine("else -> null".prependIndent().prependIndent().prependIndent())
        fileText.appendLine("}".prependIndent().prependIndent())
        fileText.appendLine(
            "as ${CommandHandler::class.simpleName!!}<TCommand, TResult>?"
                .prependIndent()
                .prependIndent()
                .prependIndent()
        )
        fileText.appendLine("}".prependIndent())
        fileText.appendLine()

        fileText.appendLine(
            "fun <TQuery : ${Query::class.simpleName!!}<TResult>, TResult : KBusResult> handlerFor("
                .prependIndent()
        )
        fileText.appendLine("query: TQuery,".prependIndent().prependIndent())
        fileText.appendLine(
            "): ${QueryHandler::class.simpleName!!}<TQuery, TResult>? {".prependIndent()
        )
        fileText.appendLine("@Suppress(\"UNCHECKED_CAST\")".prependIndent().prependIndent())
        fileText.appendLine("return when (query) {".prependIndent().prependIndent())
        queryHandlers.forEach { dependency ->
            val handlerClassName =
                messageForHandler(dependency.declaration as KSClassDeclaration)!!
                    .message
                    .qualifiedName!!
                    .asString()
            fileText.appendLine(
                "    is $handlerClassName -> this.${dependency.name}()"
                    .prependIndent()
                    .prependIndent()
            )
        }
        fileText.appendLine("else -> null".prependIndent().prependIndent().prependIndent())
        fileText.appendLine("}".prependIndent().prependIndent())
        fileText.appendLine(
            "as ${QueryHandler::class.simpleName!!}<TQuery, TResult>?"
                .prependIndent()
                .prependIndent()
                .prependIndent()
        )
        fileText.appendLine("}".prependIndent())
        fileText.appendLine()

        for (dependency in handlers) {
            if (dependency.declaration !is KSClassDeclaration) {
                continue
            }
            val string =
                generateLoaderValOverride(
                        dependency,
                        dependency.declaration,
                        allDependencies,
                        commandDependenciesProps,
                        "this.dependencies",
                    )
                    .toString()
            fileText.appendLine(string.prependIndent())
        }

        fileText.appendLine("}")
        fileText.appendLine()

        return fileText
    }

    @Suppress("ReturnCount")
    private fun generateLoaderValOverride(
        dependency: NestedDependency,
        dependencyDeclaration: KSClassDeclaration,
        allDependencies: Set<NestedDependency>,
        commandDependenciesProps: CommandDependencyProperties,
        importReferencePrefix: String = "this",
    ): StringBuilder {
        val dependencyName = dependency.name
        val dependencyTypeWithArgs = dependency.getTypeWithArgs()

        val dependencyConstructorParams =
            constructorParams(dependency, dependencyDeclaration, allDependencies)
        val dependencyTypeWithoutArgs = dependencyDeclaration.qualifiedName!!.asString()

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
            val paramNames =
                combineParameterNames(functionConstructorParamNames, importReferencePrefix)
            val loaderMethodCode = StringBuilder()
            loaderMethodCode.appendLine(
                generateAbstractFunctionDependency(
                    dependencyName,
                    dependencyTypeWithArgs,
                    withCommandDependencies = dependency.isCommandDependency,
                )
            )
            loaderMethodCode.appendLine("    = $dependencyTypeWithoutArgs($paramNames)")
            return loaderMethodCode
        } else if (isSingleton(dependency)) {
            val paramNames =
                combineParameterNames(
                    dependencyConstructorParams.map { it.name },
                    importReferencePrefix,
                )
            val loaderMethodCode = StringBuilder()
            loaderMethodCode.appendLine("val $dependencyName: $dependencyTypeWithArgs by lazy {")
            loaderMethodCode.appendLine("    $dependencyTypeWithoutArgs($paramNames)")
            loaderMethodCode.appendLine("}")
            return loaderMethodCode
        } else {
            val paramNames =
                combineParameterNames(
                    dependencyConstructorParams.map { it.name },
                    importReferencePrefix,
                )
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

    private fun combineParameterNames(dependencies: List<String>, referencePrefix: String): String {
        return dependencies
            .map { it }
            .joinToString(", ") {
                it.takeIf { it.startsWith("commandDependencies") } ?: "$referencePrefix.$it"
            }
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
        withCommandDependencies: Boolean = true,
    ): String {
        val commandDependenciesType = CommandDependencies::class.qualifiedName!!
        val commandDependenciesParam =
            "commandDependencies: $commandDependenciesType".takeIf { withCommandDependencies } ?: ""
        return "fun $dependencyName($commandDependenciesParam): $dependencyTypeWithArgs"
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
        return dependency.isCommandDependency ||
            dependency.isCommandHandler() ||
            dependency.isQueryHandler()
    }

    private fun messageForHandler(handlerClass: KSClassDeclaration): HandlerDefinition? {
        val possibleHandleMethods =
            handlerClass.getDeclaredFunctions().filter {
                it.simpleName.asString() == "handle" && it.parameters.count() == 1
            }

        val validHandlerMethods =
            possibleHandleMethods.mapNotNull { isValidHandleMethod(it, handlerClass) }

        return when (validHandlerMethods.count()) {
            1 -> validHandlerMethods.first()
            0 -> null
            else -> {
                logger.error("Multiple valid 'handle' functions found for handler", handlerClass)
                null
            }
        }
    }

    private fun isValidHandleMethod(
        handleFunction: KSFunctionDeclaration,
        handlerClass: KSClassDeclaration,
    ): HandlerDefinition? {
        val messageClass = handleFunction.parameters.first().type.resolve().declaration

        if (messageClass !is KSClassDeclaration) {
            return null
        }

        val messageTypeDeclaration = findBaseClass(messageClass)
        val messageType =
            LOADABLE_MESSAGES.find {
                it.qualifiedName == messageTypeDeclaration?.qualifiedName?.asString()
            }

        return messageType?.let { HandlerDefinition(handlerClass, messageClass, it) }
    }
}
