package com.jimbroze.kbus.core

class CommandExecutor(private val middlewares: List<Middleware>, private val busAccess: BusAccess) {
    suspend fun <TCommand : Command, TReturn : Any?, TFailure : FailureReason> execute(
        command: TCommand,
        handler: CommandHandler<TCommand, TReturn, TFailure>,
    ): BusResult<TReturn, TFailure> {
        handler.setBus(busAccess)

        val finalHandler: suspend (TCommand) -> Any? = { message: TCommand ->
            handler.handle(message)
        }

        val execute = createMiddlewareChain(finalHandler, middlewares)

        return execute(command) as BusResult<TReturn, TFailure>
    }
}
