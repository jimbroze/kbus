package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareInvocationContextFactory
import com.jimbroze.kbus.core.middleware.MiddlewareScope
import com.jimbroze.kbus.core.middleware.createMiddlewareChain
import com.jimbroze.kbus.core.module.CommandOwner
import com.jimbroze.kbus.core.uow.InvocationDomainEventPublisher
import com.jimbroze.kbus.core.uow.UnitOfWork

class CommandExecutor(
    private val transactionManager: TransactionManager?,
    private val middlewares: List<Middleware>,
    private val contextFactory: MiddlewareInvocationContextFactory,
    private val commandDependenciesFactory: CommandDependenciesFactory,
    private val invocationFactory: CommandInvocationFactory,
) {
    private val nestedMiddlewares = middlewares.filter { it.scope == MiddlewareScope.EveryCommand }

    suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
        command: TCommand,
        owner: CommandOwner,
        createHandler: (CommandDependencies) -> CommandHandler<TCommand, TResult>,
    ): TResult {
        val invocation = invocationFactory.create<TResult>(owner.domainEventDispatcher)
        val nestedCommandExecutor =
            InvocationNestedCommandExecutor(
                owner,
                invocation,
                commandDependenciesFactory,
                nestedMiddlewares,
                contextFactory,
            )
        val handler =
            createHandler(commandDependenciesFactory.create(invocation, nestedCommandExecutor))

        handler.setPublisher(invocation.integrationEventPublisher)

        val finalHandler: suspend (TCommand) -> TResult = { message: TCommand ->
            executeInUnitOfWork(message, handler, invocation.unitOfWork)
        }

        val execute =
            createMiddlewareChain(finalHandler, middlewares, contextFactory.contextFor(invocation))

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
    fun create(
        invocation: CommandInvocation<*>,
        nestedCommandExecutor: NestedCommandExecutor,
    ): CommandDependencies
}

class DefaultCommandDependenciesFactory : CommandDependenciesFactory {
    override fun create(
        invocation: CommandInvocation<*>,
        nestedCommandExecutor: NestedCommandExecutor,
    ): CommandDependencies {
        return CommandDependencies(
            InvocationDomainEventPublisher(invocation.domainEventDispatcher, invocation),
            nestedCommandExecutor,
        )
    }
}
