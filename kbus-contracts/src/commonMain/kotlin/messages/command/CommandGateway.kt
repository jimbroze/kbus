package com.jimbroze.kbus.contracts.messages.command

import com.jimbroze.kbus.contracts.result.KBusResult

/**
 * The one command a caller is allowed to send across a boundary, as a dependency it can be given.
 *
 * A context that needs work done elsewhere depends on this rather than on a bus: the type names the
 * single command it may send and the result it gets back, so nothing else on the bus is reachable
 * from there, and the module holding it need not see the assembled bus at all.
 */
interface CommandGateway<in TCommand : Command<TResult>, TResult : KBusResult> {
    suspend fun execute(command: TCommand): TResult
}
