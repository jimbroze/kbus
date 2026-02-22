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

const val COMBINED_DEPENDENCIES_INTERFACE_NAME = "AllDependencies"
const val COMBINED_HANDLERS_INTERFACE_NAME = "AllHandlers"

const val LOADER_CLASS_NAME = "AutoLoader"
const val HANDLER_FACTORY_CLASS_NAME = "HandlerFactory"

const val BUS_CLASS_NAME = "CompileTimeLoadedMessageBus"

const val DEPENDENCIES_INDEX_NAME = "DependenciesIndex"

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
        DEPENDENCIES_INTERFACE_NAME,
        COMBINED_DEPENDENCIES_INTERFACE_NAME,
        packagePath(environment),
    )

private fun handlersInterfaceGenerator(environment: SymbolProcessorEnvironment) =
    HandlersInterfaceGenerator(
        environment.codeGenerator,
        environment.logger,
        HANDLERS_INTERFACE_NAME,
        COMBINED_HANDLERS_INTERFACE_NAME,
        packagePath(environment),
    )

private fun dependencyIndexGenerator(
    environment: SymbolProcessorEnvironment
): DependencyIndexGenerator {
    val classNameSuffix =
        environment.options["kbus.moduleName"]?.split('-', '_')?.joinToString("") { segment ->
            segment.replaceFirstChar { it.uppercase() }
        }
    val className =
        classNameSuffix?.takeIf { it.isNotBlank() }?.let { "${DEPENDENCIES_INDEX_NAME}$it" }
            ?: DEPENDENCIES_INDEX_NAME

    return DependencyIndexGenerator(
        environment.codeGenerator,
        environment.logger,
        className,
        "com.jimbroze.kbus.generated.indexes",
    )
}

private fun handlersFactoryGenerator(environment: SymbolProcessorEnvironment) =
    HandlersFactoryGenerator(
        environment.codeGenerator,
        environment.logger,
        HANDLER_FACTORY_CLASS_NAME,
        COMBINED_DEPENDENCIES_INTERFACE_NAME,
        COMBINED_HANDLERS_INTERFACE_NAME,
        packagePath(environment),
    )

private fun autoLoaderGenerator(environment: SymbolProcessorEnvironment) =
    AutoLoaderGenerator(
        environment.codeGenerator,
        environment.logger,
        COMBINED_DEPENDENCIES_INTERFACE_NAME,
        LOADER_CLASS_NAME,
        packagePath(environment),
    )

private fun busGenerator(environment: SymbolProcessorEnvironment) =
    BusGenerator(
        environment.codeGenerator,
        environment.logger,
        BusConfig(
            BUS_CLASS_NAME,
            COMBINED_DEPENDENCIES_INTERFACE_NAME,
            HANDLER_FACTORY_CLASS_NAME,
            BaseMessageBus::class,
            Middleware::class,
            TransactionManager::class,
            GenerationHandlerLocator::class,
        ),
        packagePath(environment),
    )

private fun packagePath(environment: SymbolProcessorEnvironment): String {
    val packagePath = "com.jimbroze.kbus.generated"

    val sanitizedPathSuffix = environment.options["kbus.moduleName"]?.replace("-", "_")

    return sanitizedPathSuffix?.takeIf { it.isNotBlank() }?.let { "$packagePath.$it" }
        ?: packagePath
}

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
        )
    }
}
