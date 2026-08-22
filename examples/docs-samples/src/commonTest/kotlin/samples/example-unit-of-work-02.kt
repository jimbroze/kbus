// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleUnitOfWork02

import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.api.result.MessageFailure
import com.jimbroze.kbus.example.fixtures.TransferFunds

class TransferFundsHandler : CommandHandler<TransferFunds, BusResult<Unit, MessageFailure>>() {

    override suspend fun handle(message: TransferFunds): BusResult<Unit, MessageFailure> {
        // This runs inside a transaction (default behavior)
        return BusResult.success(Unit)
    }
}
