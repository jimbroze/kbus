package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.contracts.bus.BusAccess
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.core.messages.event.DomainEventDispatcher
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.createMiddlewareChain
import com.jimbroze.kbus.core.uow.DefaultUnitOfWorkFactory
import com.jimbroze.kbus.core.uow.UnitOfWork
import com.jimbroze.kbus.core.uow.UnitOfWorkDomainEventPublisher
import com.jimbroze.kbus.core.uow.UnitOfWorkFactory

class CommandExecutor(
    private val transactionManager: TransactionManager?,
    private val middlewares: List<Middleware>,
    private val busAccess: BusAccess,
    private val commandDependenciesFactory: CommandDependenciesFactory,
    private val unitOfWorkFactory: UnitOfWorkFactory = DefaultUnitOfWorkFactory(),
) {
    suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
        command: TCommand,
        createHandler: (CommandDependencies) -> CommandHandler<TCommand, TResult>,
    ): TResult {
        val unitOfWork = unitOfWorkFactory.create<TResult>()
        val handler = createHandler(commandDependenciesFactory.create(unitOfWork))

        handler.setBus(busAccess)

        val finalHandler: suspend (TCommand) -> TResult = { message: TCommand ->
            executeInUnitOfWork(message, handler, unitOfWork)
        }

        val execute = createMiddlewareChain(finalHandler, middlewares)

        return execute(command)
    }

    private suspend fun <TCommand : Command<TResult>, TResult : KBusResult> executeInUnitOfWork(
        message: TCommand,
        handler: CommandHandler<TCommand, TResult>,
        unitOfWork: UnitOfWork<TResult>,
    ): TResult {
        unitOfWork.setReturningWork { handler.handle(message) }

        val transactionConfig = handler.executeInTransaction
        if (transactionConfig != null) {
            unitOfWork.useTransaction(
                transactionConfig.transactionManagerOverride
                    ?: transactionManager
                    ?: error("Transaction Manager has not been set")
            )
        }

        return unitOfWork.execute()
    }
}

interface CommandDependenciesFactory {
    fun create(unitOfWork: UnitOfWork<*>): CommandDependencies
}

class DefaultCommandDependenciesFactory(private val domainEventDispatcher: DomainEventDispatcher?) :
    CommandDependenciesFactory {
    override fun create(unitOfWork: UnitOfWork<*>): CommandDependencies {
        return CommandDependencies(
            UnitOfWorkDomainEventPublisher(domainEventDispatcher, unitOfWork)
        )
    }
}
