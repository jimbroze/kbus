package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.core.BusResult
import com.jimbroze.kbus.core.CommandDependencies
import com.jimbroze.kbus.core.MessageBus
import com.jimbroze.kbus.core.MessageFailure
import com.jimbroze.kbus.core.Middleware
import com.jimbroze.kbus.core.TransactionManager

class CompileTimeLoadedMessageBus(
    middleware: List<Middleware>,
    transactionManager: TransactionManager,
    loader: AbstractGeneratedDIContainer,
) : MessageBus(GeneratedHandlerLocator(loader), transactionManager, middleware) {
    private val locator = GeneratedHandlerLocator(loader)

    suspend fun execute(
        command: com.jimbroze.kbus.generation.test.TestGeneratorCommand
    ): BusResult<Any, MessageFailure> {
        val handlerCreator = { commandDependencies: CommandDependencies ->
            locator.generatedHandlerFactory.testGeneratorCommandHandler(commandDependencies)
        }

        return commandExecutor.execute(command, handlerCreator)
    }

    suspend fun execute(
        command: com.jimbroze.kbus.generation.test.TestDuplicateGeneratorCommand
    ): BusResult<Any, MessageFailure> {
        val handlerCreator = { commandDependencies: CommandDependencies ->
            locator.generatedHandlerFactory.testDuplicateGeneratorCommandHandler(
                commandDependencies
            )
        }

        return commandExecutor.execute(command, handlerCreator)
    }

    suspend fun fetch(
        query: com.jimbroze.kbus.generation.test.TestGeneratorQuery
    ): BusResult<Any, MessageFailure> {
        val handler = locator.generatedHandlerFactory.testGeneratorQueryHandler()

        return queryFetcher.fetch(query, handler)
    }
}
