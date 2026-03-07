package com.jimbroze.kbus.core.registry

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.BusResult.Companion.success
import com.jimbroze.kbus.contracts.result.MessageFailure

// TODO move fixtures

class ReturnCommand(val messageData: String) : Command<BusResult<String, MessageFailure>>()

class ReturnCommandHandler : CommandHandler<ReturnCommand, BusResult<String, MessageFailure>>() {
    override suspend fun handle(message: ReturnCommand): BusResult<String, MessageFailure> {
        return success(message.messageData)
    }
}

open class StorageCommand(val messageData: String, val listStore: MutableList<String>) :
    Command<BusResult<Unit, MessageFailure>>()

class StorageCommandHandler : CommandHandler<StorageCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: StorageCommand): BusResult<Unit, MessageFailure> {
        message.listStore.add(message.messageData)
        return success(Unit)
    }
}

class AnyCommandHandler :
    CommandHandler<Command<BusResult<Unit, MessageFailure>>, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(
        message: Command<BusResult<Unit, MessageFailure>>
    ): BusResult<Unit, MessageFailure> {
        return success(Unit)
    }
}

open class StorageEvent(val eventData: String, val listStore: MutableList<String>) :
    IntegrationEvent()

class PrintEventHandler : EventHandler<StorageEvent> {
    override suspend fun handle(message: StorageEvent) {
        message.listStore.add(message.eventData)
    }
}

class OtherPrintEventHandler(val toPrint: String) : EventHandler<StorageEvent> {
    override suspend fun handle(message: StorageEvent) {
        message.listStore.add(toPrint)
    }
}
