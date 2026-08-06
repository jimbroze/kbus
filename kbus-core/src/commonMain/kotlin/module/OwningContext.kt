package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.messages.command.NestedCommandExecutor
import com.jimbroze.kbus.core.messages.event.dispatch.DomainEventDispatcher

/**
 * The two things about a command's owning context that executing the command needs: where its
 * domain events go, and which command handlers the context has.
 *
 * [handlerFor] is a lookup rather than a fixed handler because a command executed from inside
 * another command's handler is not known where the outer one was resolved. It returns null for a
 * command the context does not own, which is how a call across a context boundary surfaces as a
 * missing handler rather than a boundary the framework polices at runtime.
 */
interface OwningContext {
    val domainEventDispatcher: DomainEventDispatcher

    fun <TCommand : Command<TResult>, TResult : KBusResult> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TResult>?
}

/**
 * A context that can present a [NestedCommandExecutor] as [TCommands], the typed view of the
 * commands it owns.
 *
 * [TCommands] is what ties a context to the handlers built against it: a handler creator that asks
 * for one context's commands cannot be executed against another context, because the two no longer
 * share a type. A context with no typed view of its own satisfies this with [NestedCommandExecutor]
 * itself.
 */
interface CommandOwningContext<TCommands : NestedCommandExecutor> : OwningContext {
    fun typedCommands(nestedCommandExecutor: NestedCommandExecutor): TCommands
}
