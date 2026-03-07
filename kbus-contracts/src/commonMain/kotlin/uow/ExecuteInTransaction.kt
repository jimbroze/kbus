package com.jimbroze.kbus.contracts.uow

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.contracts.result.ResultReturningMessageHandler

interface ExecuteInTransaction<TCommand : Command<TResult>, TResult : KBusResult> :
    ResultReturningMessageHandler<TCommand, TResult> {
    val transactionManager: TransactionManager?
        get() = null
}
