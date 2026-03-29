// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleUnitOfWork03

import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.contracts.uow.TransactionConfig
import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.example.fixtures.TransferFunds

class TransferFundsHandler(
    transactionManager: TransactionManager
) : CommandHandler<TransferFunds, BusResult<Unit, MessageFailure>>() {
    override val executeInTransaction: TransactionConfig? =
        TransactionConfig(transactionManagerOverride = transactionManager)

    override suspend fun handle(message: TransferFunds): BusResult<Unit, MessageFailure> {
        // This runs inside a transaction with a custom TransactionManager
        return BusResult.success(Unit)
    }
}
