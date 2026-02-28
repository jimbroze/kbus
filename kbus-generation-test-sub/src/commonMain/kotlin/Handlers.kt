package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.core.uow.ExecuteInTransaction
import com.test.external.ExternalInterface

class ExternalDependenciesCommandSub(val messageData: String) :
    Command<BusResult<Any, MessageFailure>>()

@LoadMessageHandler
@Suppress("unused")
class ExternalDependenciesCommandHandlerSub(
    private val externalInterface: ExternalInterface,
    private val containsExternalEmptySub: ContainsExternalEmptySub,
    private val containsExternalNestedPrimitiveSub: ContainsExternalNestedPrimitiveSub,
    private val containsExternalNestedExternalSub: ContainsExternalNestedExternalSub,
) :
    CommandHandler<ExternalDependenciesCommandSub, BusResult<Any, MessageFailure>>(),
    ExecuteInTransaction<ExternalDependenciesCommandSub, BusResult<Any, MessageFailure>> {
    override suspend fun handle(
        message: ExternalDependenciesCommandSub
    ): BusResult<Any, MessageFailure> {
        return BusResult.success("success")
    }
}
