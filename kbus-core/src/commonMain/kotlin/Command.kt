package com.jimbroze.kbus.core

import kotlin.reflect.KClass

abstract class Command : Message() {
    override val messageType: String = "command"
}

abstract class CommandHandler<TCommand : Command, TReturn : Any?, TFailure : FailureReason> :
    MessageHandler<TCommand>, ResultReturningHandler<TCommand, TReturn, TFailure>, CanAccessBus() {
    abstract override suspend fun handle(message: TCommand): BusResult<TReturn, TFailure>
}

class TooManyHandlersException(
    message: String = "Only one handler can be registered for a command or query"
) : Exception(message) {
    constructor(
        handlerCls: KClass<out MessageHandler<*>>
    ) : this("The handler $handlerCls has already been registered")
}
