// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleResults03

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.middleware.BusLockedFailure
import com.jimbroze.kbus.api.middleware.LockingCommand
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.api.result.MessageFailure

sealed interface ImportPricesFailure : MessageFailure {
    data class BusLocked(val cause: BusLockedFailure) : ImportPricesFailure
}

class ImportPrices :
    Command<BusResult<Unit, ImportPricesFailure>>(),
    LockingCommand<BusResult<Unit, ImportPricesFailure>> {

    override fun busLockedFailure(failure: BusLockedFailure): BusResult<Unit, ImportPricesFailure> =
        BusResult.failure(ImportPricesFailure.BusLocked(failure))
}
