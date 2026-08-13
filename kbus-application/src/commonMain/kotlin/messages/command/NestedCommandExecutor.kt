package com.jimbroze.kbus.application.messages.command

import com.jimbroze.kbus.api.common.MissingHandlerException
import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.result.KBusResult

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
