package com.jimbroze.kbus.generation.provider

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.jimbroze.kbus.core.bus.BaseMessageBus
import com.jimbroze.kbus.core.bus.MessageBus
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
import com.jimbroze.kbus.generation.processing.dependencies.DependencyFactory
import com.jimbroze.kbus.generation.processing.dependencies.DependencyIndexFactory
import com.jimbroze.kbus.generation.processing.handlers.HandlerFactory
import com.jimbroze.kbus.generation.processors.ContainerGenerators
import com.jimbroze.kbus.generation.processors.DependencyProcessor

val KBUS_BUS_PACKAGE_NAME =
    MessageBus::class.qualifiedName!!.split(".").dropLast(1).joinToString(".")

const val DEPENDENCIES_INTERFACE_NAME = "AllDependencies"
const val HANDLERS_INTERFACE_NAME = "AllHandlers"

const val LOADER_CLASS_NAME = "AutoLoader"
const val HANDLER_FACTORY_CLASS_NAME = "HandlerFactory"

const val BUS_CLASS_NAME = "CompileTimeLoadedMessageBus"

const val DEPENDENCIES_INDEX_NAME = "DependenciesIndex"

const val MODULE_NAME_KEY = "kbus.moduleName"

private const val PACKAGE_PATH = "com.jimbroze.kbus.generated"

private fun dependencyFactory(environment: SymbolProcessorEnvironment) =
    DependencyFactory(KBUS_BUS_PACKAGE_NAME, environment.logger)

private fun handlerFactory(
    environment: SymbolProcessorEnvironment,
    dependencyFactory: DependencyFactory,
) = HandlerFactory(environment.logger, dependencyFactory)

private fun dependencyIndexFactory(environment: SymbolProcessorEnvironment) =
    DependencyIndexFactory(environment.logger)

private fun containerInterfaceGenerator(environment: SymbolProcessorEnvironment) =
    ContainerInterfaceGenerator(
        environment.codeGenerator,
        environment.logger,
        moduleClassName(environment, DEPENDENCIES_INTERFACE_NAME),
        PACKAGE_PATH,
    )

private fun handlersInterfaceGenerator(environment: SymbolProcessorEnvironment) =
    HandlersInterfaceGenerator(
        environment.codeGenerator,
        environment.logger,
        moduleClassName(environment, HANDLERS_INTERFACE_NAME),
        PACKAGE_PATH,
    )

private fun dependencyIndexGenerator(
    environment: SymbolProcessorEnvironment
): DependencyIndexGenerator {
    return DependencyIndexGenerator(
        environment.codeGenerator,
        environment.logger,
        moduleClassName(environment, DEPENDENCIES_INDEX_NAME),
        "com.jimbroze.kbus.generated.indexes",
    )
}

private fun moduleClassName(environment: SymbolProcessorEnvironment, name: String): String {
    val classNameSuffix =
        environment.options[MODULE_NAME_KEY]?.split('-', '_')?.joinToString("") { segment ->
            segment.replaceFirstChar { it.uppercase() }
        }

    return classNameSuffix?.takeIf { it.isNotBlank() }?.let { "$name$it" } ?: name
}

private fun handlersFactoryGenerator(environment: SymbolProcessorEnvironment) =
    HandlersFactoryGenerator(
        environment.codeGenerator,
        environment.logger,
        moduleClassName(environment, HANDLER_FACTORY_CLASS_NAME),
        moduleClassName(environment, DEPENDENCIES_INTERFACE_NAME),
        moduleClassName(environment, HANDLERS_INTERFACE_NAME),
        PACKAGE_PATH,
    )

private fun autoLoaderGenerator(environment: SymbolProcessorEnvironment) =
    AutoLoaderGenerator(
        environment.codeGenerator,
        environment.logger,
        moduleClassName(environment, DEPENDENCIES_INTERFACE_NAME),
        moduleClassName(environment, LOADER_CLASS_NAME),
        PACKAGE_PATH,
    )

private fun busGenerator(environment: SymbolProcessorEnvironment) =
    BusGenerator(
        environment.codeGenerator,
        environment.logger,
        BusConfig(
            moduleClassName(environment, BUS_CLASS_NAME),
            moduleClassName(environment, DEPENDENCIES_INTERFACE_NAME),
            moduleClassName(environment, HANDLER_FACTORY_CLASS_NAME),
            BaseMessageBus::class,
            Middleware::class,
            TransactionManager::class,
            GenerationHandlerLocator::class,
        ),
        PACKAGE_PATH,
    )

class ContainerProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val dependencyFactory = dependencyFactory(environment)
        return DependencyProcessor(
            environment.logger,
            handlerFactory(environment, dependencyFactory),
            dependencyIndexFactory(environment),
            ContainerGenerators(
                containerInterfaceGenerator(environment),
                handlersInterfaceGenerator(environment),
                autoLoaderGenerator(environment),
                handlersFactoryGenerator(environment),
                dependencyIndexGenerator(environment),
                busGenerator(environment),
            ),
            shouldGenerateBus(environment),
        )
    }

    private fun shouldGenerateBus(environment: SymbolProcessorEnvironment): Boolean {
        return environment.options[MODULE_NAME_KEY].isNullOrEmpty()
    }
}
