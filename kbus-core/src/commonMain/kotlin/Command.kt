package com.jimbroze.kbus.core

import kotlin.reflect.KClass

abstract class Command<TReturn : Any?, TMessageFailure : MessageFailure> :
    ResultReturningMessage<TReturn, TMessageFailure> {
    override val messageType: String = "command"

    final override fun toString(): String = this::class.simpleName ?: "Command"
}

abstract class CommandHandler<
    TCommand : Command<TReturn, TMessageFailure>,
    TReturn : Any?,
    TMessageFailure : MessageFailure,
> : ResultReturningMessageHandler<TCommand, TReturn, TMessageFailure>, CanAccessBus() {
    abstract override suspend fun handle(message: TCommand): BusResult<TReturn, TMessageFailure>
}

class TooManyHandlersException(
    message: String = "Only one handler can be registered for a command or query"
) : Exception(message) {
    constructor(
        handlerCls: KClass<out MessageHandler<*>>
    ) : this("The handler $handlerCls has already been registered")
}
