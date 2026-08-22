package com.jimbroze.kbus.generation.provider

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.jimbroze.kbus.core.boundedcontext.inbox.InboxTuning
import com.jimbroze.kbus.core.bus.BaseMessageBus
import com.jimbroze.kbus.core.middleware.infrastructure.Middleware
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.generation.generators.AutoLoaderGenerator
import com.jimbroze.kbus.generation.generators.AutoPublishRegistrationsGenerator
import com.jimbroze.kbus.generation.generators.BusConfig
import com.jimbroze.kbus.generation.generators.BusGenerator
import com.jimbroze.kbus.generation.generators.CommandGatewayGenerator
import com.jimbroze.kbus.generation.generators.ContainerInterfaceGenerator
import com.jimbroze.kbus.generation.generators.ContextCommandsGenerator
import com.jimbroze.kbus.generation.generators.DependencyIndexGenerator
import com.jimbroze.kbus.generation.generators.HandlersFactoryGenerator
import com.jimbroze.kbus.generation.generators.HandlersInterfaceGenerator
import com.jimbroze.kbus.generation.generators.LoadedEventHandlersGenerator
import com.jimbroze.kbus.generation.processing.IndexParser
import com.jimbroze.kbus.generation.processing.autopublish.AutoPublishFactory
import com.jimbroze.kbus.generation.processing.dependencies.DependencyFactory
import com.jimbroze.kbus.generation.processing.handlers.HandlerFactory
import com.jimbroze.kbus.generation.processors.CodeGenerators
import com.jimbroze.kbus.generation.processors.KbusProcessor
import com.jimbroze.kbus.infrastructure.transaction.TransactionManager

private const val GENERATION_ROOT_PACKAGE_PATH = "com.jimbroze.kbus.generated"

private const val DEPENDENCIES_INTERFACE_NAME = "AllDependencies"
private const val HANDLERS_INTERFACE_NAME = "Handlers"
private const val LOADER_CLASS_NAME = "AutoLoader"
private const val HANDLER_FACTORY_CLASS_NAME = "HandlerFactory"
private const val BUS_CLASS_NAME = "CompileTimeLoadedMessageBus"
private const val CONTEXT_CLASS_NAME = "Context"
private const val DEPENDENCIES_INDEX_NAME = "DependenciesIndex"
private const val LOADED_DOMAIN_EVENT_HANDLERS_NAME = "LoadedDomainEventHandlers"
private const val LOADED_INTEGRATION_EVENT_HANDLERS_NAME = "LoadedIntegrationEventHandlers"
private const val AUTO_PUBLISH_REGISTRATIONS_NAME = "GeneratedAutoPublishRegistrations"
private const val CONTEXT_COMMANDS_INTERFACE_NAME = "Commands"
private const val CONTEXT_COMMAND_EXECUTOR_NAME = "CommandExecutor"
private const val COMMAND_GATEWAY_CLASS_SUFFIX = "Gateway"

private const val MODULE_NAME_KEY = "kbus.subModuleName"
private const val BOUNDED_CONTEXT_IDENTITY_KEY = "kbus.boundedContextIdentity"
private const val INDEX_PACKAGE_KEY = "kbus.indexPackage"
private const val MODULES_TO_INDEX_KEY = "kbus.modulesToIndex"

class ContainerProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val config = KBusProcessorConfig(environment)
        config.reportUnusableOptions(environment.logger)
        val dependencyFactory = DependencyFactory(environment.logger)

        return KbusProcessor(
            environment.logger,
            HandlerFactory(environment.logger, dependencyFactory, config.boundedContextIdentity),
            IndexParser(environment.logger),
            AutoPublishFactory(environment.logger),
            createGenerators(environment, config),
            config.isSubModule,
            config.candidateIndexClassNames,
        )
    }

    private fun createGenerators(env: SymbolProcessorEnvironment, config: KBusProcessorConfig) =
        CodeGenerators(
            ContainerInterfaceGenerator(
                env.codeGenerator,
                env.logger,
                DEPENDENCIES_INTERFACE_NAME,
                config.generatedPackagePath,
            ),
            HandlersInterfaceGenerator(
                env.codeGenerator,
                env.logger,
                HANDLERS_INTERFACE_NAME,
                config.generatedPackagePath,
            ),
            AutoLoaderGenerator(
                env.codeGenerator,
                env.logger,
                DEPENDENCIES_INTERFACE_NAME,
                LOADER_CLASS_NAME,
                config.generatedPackagePath,
            ),
            HandlersFactoryGenerator(
                env.codeGenerator,
                env.logger,
                HANDLER_FACTORY_CLASS_NAME,
                DEPENDENCIES_INTERFACE_NAME,
                HANDLERS_INTERFACE_NAME,
                CONTEXT_COMMAND_EXECUTOR_NAME,
                config.generatedPackagePath,
            ),
            DependencyIndexGenerator(
                env.codeGenerator,
                env.logger,
                config.moduleClassName(DEPENDENCIES_INDEX_NAME),
                config.indexPackagePath,
            ),
            createBusGenerator(env, config),
            LoadedEventHandlersGenerator(
                env.codeGenerator,
                env.logger,
                LOADED_DOMAIN_EVENT_HANDLERS_NAME,
                LOADED_INTEGRATION_EVENT_HANDLERS_NAME,
                config.generatedPackagePath,
            ),
            AutoPublishRegistrationsGenerator(
                env.codeGenerator,
                env.logger,
                AUTO_PUBLISH_REGISTRATIONS_NAME,
                config.generatedPackagePath,
            ),
            ContextCommandsGenerator(
                env.codeGenerator,
                env.logger,
                CONTEXT_COMMANDS_INTERFACE_NAME,
                CONTEXT_COMMAND_EXECUTOR_NAME,
                config.generatedPackagePath,
            ),
            createCommandGatewayGenerator(env, config),
        )

    private fun createCommandGatewayGenerator(
        env: SymbolProcessorEnvironment,
        config: KBusProcessorConfig,
    ) =
        CommandGatewayGenerator(
            env.codeGenerator,
            env.logger,
            COMMAND_GATEWAY_CLASS_SUFFIX,
            config.generatedPackagePath,
        )

    private fun createBusGenerator(env: SymbolProcessorEnvironment, config: KBusProcessorConfig) =
        BusGenerator(
            env.codeGenerator,
            env.logger,
            BusConfig(
                BUS_CLASS_NAME,
                DEPENDENCIES_INTERFACE_NAME,
                HANDLER_FACTORY_CLASS_NAME,
                CONTEXT_CLASS_NAME,
                CONTEXT_COMMAND_EXECUTOR_NAME,
                BaseMessageBus::class,
                Middleware::class,
                TransactionManager::class,
                OutboxConfig::class,
                InboxTuning::class,
            ),
            config.generatedPackagePath,
        )
}

private class KBusProcessorConfig(private val environment: SymbolProcessorEnvironment) {
    val isSubModule: Boolean
        get() = !(environment.options[MODULE_NAME_KEY].isNullOrEmpty())

    /**
     * This Gradle module's bounded context identity — orthogonal to [MODULE_NAME_KEY]: a bounded
     * context often spans several Gradle modules, which are separate submodules but one identity.
     */
    val boundedContextIdentity: String
        get() = environment.options[BOUNDED_CONTEXT_IDENTITY_KEY]?.trim().orEmpty()

    /**
     * A build arg that cannot mean what it was set to. Blank whitespace reads as an identity but
     * names no context, so every handler in the module would be folded into the default one.
     */
    fun reportUnusableOptions(logger: KSPLogger) {
        val declaredIdentity = environment.options[BOUNDED_CONTEXT_IDENTITY_KEY]
        if (declaredIdentity != null && declaredIdentity.isBlank()) {
            logger.error(
                "$BOUNDED_CONTEXT_IDENTITY_KEY is set to blank whitespace, which names no bounded " +
                    "context. Give it an identity, or remove the build arg to leave this " +
                    "module's handlers in the default context."
            )
        }
    }

    val indexPackagePath: String
        get() {
            val path = environment.options[INDEX_PACKAGE_KEY]
            require(!path.isNullOrBlank()) {
                "Index package path must be provided via $INDEX_PACKAGE_KEY environment option"
            }
            return path
        }

    /**
     * The index class each named module would have generated. A name is a candidate, not a promise:
     * a module that declares no handlers generates no index, and naming it is how a consumer says
     * "look here" without having to know which of its dependencies use kbus.
     */
    val candidateIndexClassNames: List<String>
        get() =
            environment.options[MODULES_TO_INDEX_KEY]
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { "$indexPackagePath.$DEPENDENCIES_INDEX_NAME${pascalCased(it)}" }

    /**
     * Where this module's generated code lives: a submodule gets a package of its own, so several
     * modules of one bounded context can each generate the same class names without colliding.
     */
    val generatedPackagePath: String
        get() =
            subModulePackageSegment?.let { "$GENERATION_ROOT_PACKAGE_PATH.$it" }
                ?: GENERATION_ROOT_PACKAGE_PATH

    /**
     * Index classes of every module share one package, and a consumer locates one from the module
     * name alone — so an index name, unlike the rest of a module's generated code, has to carry the
     * module it came from.
     */
    fun moduleClassName(name: String): String =
        subModuleName?.let { "$name${pascalCased(it)}" } ?: name

    private fun pascalCased(moduleName: String): String =
        nameSegments(moduleName).joinToString("") { segment ->
            segment.replaceFirstChar { it.uppercase() }
        }

    private fun nameSegments(moduleName: String): List<String> =
        moduleName.split('-', '_').filter { it.isNotBlank() }

    private val subModuleName: String?
        get() = environment.options[MODULE_NAME_KEY]?.takeIf { nameSegments(it).isNotEmpty() }

    private val subModulePackageSegment: String?
        get() =
            subModuleName
                ?.let { nameSegments(it) }
                ?.mapIndexed { index, segment ->
                    if (index == 0) segment.replaceFirstChar { it.lowercase() }
                    else segment.replaceFirstChar { it.uppercase() }
                }
                ?.joinToString("")
}
