package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.core.BaseMessageBus
import com.jimbroze.kbus.core.BusResult
import com.jimbroze.kbus.core.CommandDependencies
import com.jimbroze.kbus.core.MessageFailure
import com.jimbroze.kbus.core.Middleware
import com.jimbroze.kbus.core.TransactionManager

// TODO change to decorator Requires MessageBus interface
class CompileTimeLoadedMessageBus
private constructor(
    private val locator: GeneratedHandlerLocator,
    transactionManager: TransactionManager,
    middleware: List<Middleware>,
) : BaseMessageBus(locator, transactionManager, middleware) {

    constructor(
        loader: IContainer,
        transactionManager: TransactionManager,
        middleware: List<Middleware>,
    ) : this(
        GeneratedHandlerLocator(GeneratedHandlerFactory(loader)),
        transactionManager,
        middleware,
    )

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
        val handlerCreator = { -> locator.generatedHandlerFactory.testGeneratorQueryHandler() }

        return queryFetcher.fetch(query, handlerCreator)
    }
}
