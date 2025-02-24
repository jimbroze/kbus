package com.jimbroze.kbus.core

import kotlin.reflect.KClass

abstract class Command : Message() {
    override val messageType: String = "command"
}

abstract class CommandHandler<TCommand : Command, TReturn : Any?, TFailure : FailureReason> :
    MessageHandler<TCommand>, ResultReturningHandler<TCommand, TReturn, TFailure>, CanAccessBus() {
    abstract override suspend fun handle(message: TCommand): BusResult<TReturn, TFailure>
}

class TooManyHandlersException(message: String = "A handler has already been registered") :
    Exception(message) {
    constructor(
        messageCls: KClass<out Message>
    ) : this("A handler has already been registered for the message $messageCls")
}
