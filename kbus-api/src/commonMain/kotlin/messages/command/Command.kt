package com.jimbroze.kbus.api.messages.command

import com.jimbroze.kbus.api.common.MessageHandler
import com.jimbroze.kbus.api.common.ResultReturningMessage
import com.jimbroze.kbus.api.result.KBusResult
import com.jimbroze.kbus.api.result.ResultReturningMessageHandler
import kotlin.reflect.KClass

abstract class Command<TResult : KBusResult> : ResultReturningMessage<TResult> {
    override val messageType: String = "command"

    final override fun toString(): String = this::class.simpleName ?: "Command"
}

abstract class CommandHandler<TCommand : Command<TResult>, TResult : KBusResult> :
    ResultReturningMessageHandler<TCommand, TResult> {
    open val executeInTransaction: Boolean = true

    abstract override suspend fun handle(message: TCommand): TResult
}

/**
 * A command executed from inside another command's handler asked for a transaction where none is
 * running. Thrown rather than honoured, because the alternative is work silently committing outside
 * the transaction its handler declared.
 */
class NestedTransactionMismatchException(commandClass: KClass<out Command<*>>) :
    Exception(
        "$commandClass declared a transaction, but is nested inside a command that opened none"
    )

// TODO move to a persisted-store specific package?
class TooManyHandlersException(
    message: String = "Only one handler can be registered for a command or query"
) : Exception(message) {
    constructor(
        handlerCls: KClass<out MessageHandler<*>>
    ) : this("The handler $handlerCls has already been registered")
}
