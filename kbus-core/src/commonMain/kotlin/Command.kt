package com.jimbroze.kbus.core

import kotlin.reflect.KClass

abstract class Command<TResult : KBusResult> : ResultReturningMessage<TResult> {
    override val messageType: String = "command"

    final override fun toString(): String = this::class.simpleName ?: "Command"
}

abstract class CommandHandler<TCommand : Command<TResult>, TResult : KBusResult> :
    ResultReturningMessageHandler<TCommand, TResult>, CanAccessBus() {
    abstract override suspend fun handle(message: TCommand): TResult
}

class TooManyHandlersException(
    message: String = "Only one handler can be registered for a command or query"
) : Exception(message) {
    constructor(
        handlerCls: KClass<out MessageHandler<*>>
    ) : this("The handler $handlerCls has already been registered")
}
