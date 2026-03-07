// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleUnitOfWork02

import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.contracts.uow.ExecuteInTransaction
import com.jimbroze.kbus.example.fixtures.TransferFunds

class TransferFundsHandler : CommandHandler<TransferFunds, BusResult<Unit, MessageFailure>>(),
    ExecuteInTransaction<TransferFunds, BusResult<Unit, MessageFailure>> {

    override suspend fun handle(message: TransferFunds): BusResult<Unit, MessageFailure> {
        // This runs inside a transaction
        return BusResult.success(Unit)
    }
}
