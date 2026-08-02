package com.jimbroze.kbus.generation.provider

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.core.bus.BaseMessageBus
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.module.inbox.InboxConfig
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.generation.generators.AutoLoaderGenerator
import com.jimbroze.kbus.generation.generators.AutoPublishRegistrationsGenerator
import com.jimbroze.kbus.generation.generators.BusConfig
import com.jimbroze.kbus.generation.generators.BusGenerator
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

private const val PACKAGE_PATH = "com.jimbroze.kbus.generated"

private const val DEPENDENCIES_INTERFACE_NAME = "AllDependencies"
private const val HANDLERS_INTERFACE_NAME = "Handlers"
private const val LOADER_CLASS_NAME = "AutoLoader"
private const val HANDLER_FACTORY_CLASS_NAME = "HandlerFactory"
private const val BUS_CLASS_NAME = "CompileTimeLoadedMessageBus"
private const val DEPENDENCIES_INDEX_NAME = "DependenciesIndex"
private const val LOADED_DOMAIN_EVENT_HANDLERS_NAME = "LoadedDomainEventHandlers"
private const val LOADED_INTEGRATION_EVENT_HANDLERS_NAME = "LoadedIntegrationEventHandlers"
private const val AUTO_PUBLISH_REGISTRATIONS_NAME = "GeneratedAutoPublishRegistrations"
private const val CONTEXT_COMMANDS_INTERFACE_NAME = "Commands"
private const val CONTEXT_COMMAND_EXECUTOR_NAME = "CommandExecutor"

private const val MODULE_NAME_KEY = "kbus.subModuleName"
private const val BOUNDED_CONTEXT_IDENTITY_KEY = "kbus.boundedContextIdentity"
private const val INDEX_PACKAGE_KEY = "kbus.indexPackage"

class ContainerProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val config = KBusProcessorConfig(environment)
        val dependencyFactory = DependencyFactory(environment.logger)

        return KbusProcessor(
            environment.logger,
            HandlerFactory(environment.logger, dependencyFactory, config.boundedContextIdentity),
            IndexParser(environment.logger),
            AutoPublishFactory(environment.logger),
            createGenerators(environment, config),
            config.isSubModule,
            config.indexPackagePath,
        )
    }

    private fun createGenerators(env: SymbolProcessorEnvironment, config: KBusProcessorConfig) =
        CodeGenerators(
            ContainerInterfaceGenerator(
                env.codeGenerator,
                env.logger,
                config.moduleClassName(DEPENDENCIES_INTERFACE_NAME),
                PACKAGE_PATH,
            ),
            HandlersInterfaceGenerator(
                env.codeGenerator,
                env.logger,
                config.moduleClassName(HANDLERS_INTERFACE_NAME),
                PACKAGE_PATH,
            ),
            AutoLoaderGenerator(
                env.codeGenerator,
                env.logger,
                config.moduleClassName(DEPENDENCIES_INTERFACE_NAME),
                config.moduleClassName(LOADER_CLASS_NAME),
                PACKAGE_PATH,
            ),
            HandlersFactoryGenerator(
                env.codeGenerator,
                env.logger,
                config.moduleClassName(HANDLER_FACTORY_CLASS_NAME),
                config.moduleClassName(DEPENDENCIES_INTERFACE_NAME),
                config.moduleClassName(HANDLERS_INTERFACE_NAME),
                CONTEXT_COMMAND_EXECUTOR_NAME,
                PACKAGE_PATH,
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
                config.moduleClassName(LOADED_DOMAIN_EVENT_HANDLERS_NAME),
                config.moduleClassName(LOADED_INTEGRATION_EVENT_HANDLERS_NAME),
                PACKAGE_PATH,
            ),
            AutoPublishRegistrationsGenerator(
                env.codeGenerator,
                env.logger,
                config.moduleClassName(AUTO_PUBLISH_REGISTRATIONS_NAME),
                PACKAGE_PATH,
            ),
            ContextCommandsGenerator(
                env.codeGenerator,
                env.logger,
                config.moduleClassName(CONTEXT_COMMANDS_INTERFACE_NAME),
                CONTEXT_COMMAND_EXECUTOR_NAME,
                PACKAGE_PATH,
            ),
        )

    private fun createBusGenerator(env: SymbolProcessorEnvironment, config: KBusProcessorConfig) =
        BusGenerator(
            env.codeGenerator,
            env.logger,
            BusConfig(
                config.moduleClassName(BUS_CLASS_NAME),
                config.moduleClassName(DEPENDENCIES_INTERFACE_NAME),
                config.moduleClassName(HANDLER_FACTORY_CLASS_NAME),
                BaseMessageBus::class,
                Middleware::class,
                TransactionManager::class,
                OutboxConfig::class,
                InboxConfig::class,
            ),
            PACKAGE_PATH,
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
        get() = environment.options[BOUNDED_CONTEXT_IDENTITY_KEY].orEmpty()

    val indexPackagePath: String
        get() {
            val path = environment.options[INDEX_PACKAGE_KEY]
            require(!path.isNullOrBlank()) {
                "Index package path must be provided via $INDEX_PACKAGE_KEY environment option"
            }
            return path
        }

    fun moduleClassName(name: String): String {
        val classNameSuffix =
            environment.options[MODULE_NAME_KEY]?.split('-', '_')?.joinToString("") { segment ->
                segment.replaceFirstChar { it.uppercase() }
            }

        return classNameSuffix?.takeIf { it.isNotBlank() }?.let { "$name$it" } ?: name
    }
}
