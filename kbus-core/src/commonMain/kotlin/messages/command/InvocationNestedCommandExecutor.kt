package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.api.common.MissingHandlerException
import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.command.NestedTransactionMismatchException
import com.jimbroze.kbus.api.result.KBusResult
import com.jimbroze.kbus.application.messages.command.NestedCommandExecutor
import com.jimbroze.kbus.core.boundedcontext.OwningContext
import com.jimbroze.kbus.core.middleware.infrastructure.Middleware
import com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareInvocationContextFactory
import com.jimbroze.kbus.core.middleware.infrastructure.createMiddlewareChain

/**
 * Runs nested commands inside one [invocation]. It never touches [invocation]'s unit of work: the
 * nested handler runs within the outer command's primary work, so sharing is what the object graph
 * does rather than a flag anything consults.
 *
 * [nestedMiddlewares] is pre-filtered to those declaring
 * [EveryCommand][com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareScope.EveryCommand].
 */
internal class InvocationNestedCommandExecutor(
    private val owningContext: OwningContext,
    private val invocation: CommandInvocation<*>,
    private val dependenciesFactory: CommandDependenciesFactory,
    private val nestedMiddlewares: List<Middleware>,
    private val contextFactory: MiddlewareInvocationContextFactory,
) : NestedCommandExecutor {
    override suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
        command: TCommand
    ): TResult {
        val handler =
            owningContext.handlerFor(
                command,
                dependenciesFactory.create(owningContext, invocation, this),
            ) ?: throw MissingHandlerException(command::class)

        checkTransaction(command, handler.executeInTransaction)

        val execute =
            createMiddlewareChain<TCommand, TResult>(
                { message -> handler.handle(message) },
                nestedMiddlewares,
                contextFactory.contextFor(invocation),
            )

        return execute(command)
    }

    /**
     * A nested handler cannot open a transaction of its own — it is already inside the outer
     * command's. Anything it declares must therefore already be satisfied, or the mismatch is
     * raised here rather than left to commit somewhere the handler did not ask for.
     */
    private fun checkTransaction(command: Command<*>, executeInTransaction: Boolean) {
        if (!executeInTransaction) return
        if (invocation.unitOfWork.activeTransactionManager != null) return

        throw NestedTransactionMismatchException(command::class)
    }
}
