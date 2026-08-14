package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.api.result.KBusResult
import com.jimbroze.kbus.api.uow.TransactionManager
import com.jimbroze.kbus.application.messages.command.CommandDependencies
import com.jimbroze.kbus.application.messages.command.NestedCommandExecutor
import com.jimbroze.kbus.core.boundedcontext.CommandOwningContext
import com.jimbroze.kbus.core.boundedcontext.OwningContext
import com.jimbroze.kbus.core.middleware.infrastructure.Middleware
import com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareInvocationContextFactory
import com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareScope
import com.jimbroze.kbus.core.middleware.infrastructure.createMiddlewareChain
import com.jimbroze.kbus.core.uow.InvocationDomainEventPublisher
import com.jimbroze.kbus.core.uow.UnitOfWork

class CommandExecutor(
    private val transactionManager: TransactionManager,
    private val middlewares: List<Middleware>,
    private val contextFactory: MiddlewareInvocationContextFactory,
    private val commandDependenciesFactory: CommandDependenciesFactory,
    private val invocationFactory: CommandInvocationFactory,
) {
    private val nestedMiddlewares = middlewares.filter { it.scope == MiddlewareScope.EveryCommand }

    suspend fun <
        TCommand : Command<TResult>,
        TResult : KBusResult,
        TCommands : NestedCommandExecutor,
    > execute(
        command: TCommand,
        owningContext: CommandOwningContext<TCommands>,
        createHandler: (CommandDependencies, TCommands) -> CommandHandler<TCommand, TResult>,
    ): TResult {
        val invocation = invocationFactory.create<TResult>()
        val nestedCommandExecutor =
            InvocationNestedCommandExecutor(
                owningContext,
                invocation,
                commandDependenciesFactory,
                nestedMiddlewares,
                contextFactory,
            )
        val handler =
            createHandler(
                commandDependenciesFactory.create(owningContext, invocation, nestedCommandExecutor),
                owningContext.typedCommands(nestedCommandExecutor),
            )

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

        if (handler.executeInTransaction) {
            unitOfWork.useTransaction(transactionManager)
        }

        return unitOfWork.execute()
    }
}

interface CommandDependenciesFactory {
    fun create(
        owningContext: OwningContext,
        invocation: CommandInvocation<*>,
        nestedCommandExecutor: NestedCommandExecutor,
    ): CommandDependencies
}

class DefaultCommandDependenciesFactory : CommandDependenciesFactory {
    override fun create(
        owningContext: OwningContext,
        invocation: CommandInvocation<*>,
        nestedCommandExecutor: NestedCommandExecutor,
    ): CommandDependencies {
        return CommandDependencies(
            InvocationDomainEventPublisher(owningContext.domainEventDispatcher, invocation),
            nestedCommandExecutor,
            invocation.integrationEventPublisher,
        )
    }
}
