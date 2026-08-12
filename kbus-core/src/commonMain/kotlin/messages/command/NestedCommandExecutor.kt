package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.contracts.common.MissingHandlerException
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.NestedTransactionMismatchException
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.contracts.uow.TransactionConfig
import com.jimbroze.kbus.core.boundedcontext.OwningContext
import com.jimbroze.kbus.core.middleware.infrastructure.Middleware
import com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareInvocationContextFactory
import com.jimbroze.kbus.core.middleware.infrastructure.createMiddlewareChain

/**
 * Executes a command from inside another command's handler, sharing that command's transaction,
 * domain event phase and integration event publisher.
 *
 * Only commands the same context owns are reachable; anything else throws
 * [MissingHandlerException]. Crossing a context boundary is what the bus's own `execute` is for,
 * and that path can never share a transaction.
 *
 * An interface so a handler under test can be given a stub, without a locator or a middleware
 * chain.
 */
interface NestedCommandExecutor {
    suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
        command: TCommand
    ): TResult
}

/**
 * A generated, per-bounded-context view of [NestedCommandExecutor], with one function per command
 * that context owns and the module declaring it can see. Implemented only by generated code; a
 * handler asks for one by declaring a constructor parameter of the generated interface's type.
 *
 * The untyped [execute] remains available for a command the interface has no function for, and
 * still refuses any command another context owns.
 */
interface ContextCommands : NestedCommandExecutor

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
    private fun checkTransaction(command: Command<*>, transactionConfig: TransactionConfig?) {
        val running = invocation.unitOfWork.activeTransactionManager
        val requested = transactionConfig?.transactionManagerOverride ?: running

        if (transactionConfig == null) return
        if (running != null && requested === running) return

        throw NestedTransactionMismatchException(command::class, requested, running)
    }
}
