package com.jimbroze.kbus.generation

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.jimbroze.kbus.core.Command
import com.jimbroze.kbus.core.MessageBus
import com.jimbroze.kbus.core.Query

val KBUS_BUS_PACKAGE_NAME =
    MessageBus::class.qualifiedName!!.split(".").dropLast(1).joinToString(".")
const val BUS_CLASS_NAME = "CompileTimeLoadedMessageBus"

const val LOADER_INTERFACE_NAME = "IGeneratedDIContainer"
const val COMBINED_CONTAINER_INTERFACE_NAME = "DIContainer"
const val LOADER_CLASS_NAME = "AbstractGeneratedDIContainer"

val LOADABLE_MESSAGES = listOf(Command::class, Query::class)

private fun dependencyFactory(environment: SymbolProcessorEnvironment) =
    DependencyFactory(KBUS_BUS_PACKAGE_NAME, environment.logger)

private fun containerGenerator(environment: SymbolProcessorEnvironment) =
    ContainerGenerator(
        environment.codeGenerator,
        environment.logger,
        LOADER_INTERFACE_NAME,
        COMBINED_CONTAINER_INTERFACE_NAME,
        LOADER_CLASS_NAME,
    )

private fun loadedMessageGenerator(environment: SymbolProcessorEnvironment) =
    LoadedMessageGenerator(environment.codeGenerator, environment.logger, LOADABLE_MESSAGES)

private fun busGenerator(environment: SymbolProcessorEnvironment) =
    MessageBusGenerator(environment.codeGenerator, environment.logger, BUS_CLASS_NAME)

class MessageProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return MessageProcessor(
            logger = environment.logger,
            dependencyFactory = dependencyFactory(environment),
            containerGenerator = containerGenerator(environment),
        )
    }
}

class ContainerProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ContainerInterfaceProcessor(
            logger = environment.logger,
            dependencyFactory = dependencyFactory(environment),
            containerGenerator = containerGenerator(environment),
            loadedMessageGenerator = loadedMessageGenerator(environment),
            busGenerator = busGenerator(environment),
        )
    }
}
