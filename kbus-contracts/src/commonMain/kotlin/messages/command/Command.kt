package com.jimbroze.kbus.contracts.messages.command

import com.jimbroze.kbus.contracts.common.MessageHandler
import com.jimbroze.kbus.contracts.common.ResultReturningMessage
import com.jimbroze.kbus.contracts.messages.event.CanPublishIntegrationEvent
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.contracts.result.ResultReturningMessageHandler
import com.jimbroze.kbus.contracts.uow.TransactionConfig
import kotlin.reflect.KClass

abstract class Command<TResult : KBusResult> : ResultReturningMessage<TResult> {
    override val messageType: String = "command"

    final override fun toString(): String = this::class.simpleName ?: "Command"
}

abstract class CommandHandler<TCommand : Command<TResult>, TResult : KBusResult> :
    ResultReturningMessageHandler<TCommand, TResult>, CanPublishIntegrationEvent() {
    open val executeInTransaction: TransactionConfig? = TransactionConfig()

    abstract override suspend fun handle(message: TCommand): TResult
}

class TooManyHandlersException(
    message: String = "Only one handler can be registered for a command or query"
) : Exception(message) {
    constructor(
        handlerCls: KClass<out MessageHandler<*>>
    ) : this("The handler $handlerCls has already been registered")
}
