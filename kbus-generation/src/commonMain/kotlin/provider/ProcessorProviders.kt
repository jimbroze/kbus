package com.jimbroze.kbus.generation.provider

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.jimbroze.kbus.core.bus.BaseMessageBus
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.registry.GenerationHandlerLocator
import com.jimbroze.kbus.core.uow.TransactionManager
import com.jimbroze.kbus.generation.generators.AutoLoaderGenerator
import com.jimbroze.kbus.generation.generators.BusConfig
import com.jimbroze.kbus.generation.generators.BusGenerator
import com.jimbroze.kbus.generation.generators.ContainerInterfaceGenerator
import com.jimbroze.kbus.generation.generators.DependencyIndexGenerator
import com.jimbroze.kbus.generation.generators.HandlersFactoryGenerator
import com.jimbroze.kbus.generation.generators.HandlersInterfaceGenerator
import com.jimbroze.kbus.generation.processing.IndexParser
import com.jimbroze.kbus.generation.processing.dependencies.DependencyFactory
import com.jimbroze.kbus.generation.processing.handlers.HandlerFactory
import com.jimbroze.kbus.generation.processors.ContainerGenerators
import com.jimbroze.kbus.generation.processors.DependencyProcessor

// TODO don't need this package name anymore now that we know what is external?
private const val KBUS_CORE_PACKAGE_NAME = "com.jimbroze.kbus.core"
private const val PACKAGE_PATH = "com.jimbroze.kbus.generated"

private const val DEPENDENCIES_INTERFACE_NAME = "AllDependencies"
private const val HANDLERS_INTERFACE_NAME = "AllHandlers"
private const val LOADER_CLASS_NAME = "AutoLoader"
private const val HANDLER_FACTORY_CLASS_NAME = "HandlerFactory"
private const val BUS_CLASS_NAME = "CompileTimeLoadedMessageBus"
private const val DEPENDENCIES_INDEX_NAME = "DependenciesIndex"

private const val MODULE_NAME_KEY = "kbus.subModuleName"
private const val INDEX_PACKAGE_KEY = "kbus.indexPackage"

class ContainerProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val config = KBusProcessorConfig(environment)
        val dependencyFactory = DependencyFactory(KBUS_CORE_PACKAGE_NAME, environment.logger)

        return DependencyProcessor(
            environment.logger,
            HandlerFactory(environment.logger, dependencyFactory),
            IndexParser(environment.logger),
            createGenerators(environment, config),
            config.shouldGenerateBus,
            config.indexPackagePath,
        )
    }

    private fun createGenerators(env: SymbolProcessorEnvironment, config: KBusProcessorConfig) =
        ContainerGenerators(
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
                PACKAGE_PATH,
            ),
            DependencyIndexGenerator(
                env.codeGenerator,
                env.logger,
                config.moduleClassName(DEPENDENCIES_INDEX_NAME),
                config.indexPackagePath,
            ),
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
                    GenerationHandlerLocator::class,
                ),
                PACKAGE_PATH,
            ),
        )
}

private class KBusProcessorConfig(private val environment: SymbolProcessorEnvironment) {
    val shouldGenerateBus: Boolean
        get() = environment.options[MODULE_NAME_KEY].isNullOrEmpty()

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
