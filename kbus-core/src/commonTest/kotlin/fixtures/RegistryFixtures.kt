package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.api.messages.event.EventHandler
import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.api.result.BusResult.Companion.success
import com.jimbroze.kbus.api.result.MessageFailure
import com.jimbroze.kbus.api.uow.TransactionConfig
import kotlinx.coroutines.delay

class ReturnCommand(val messageData: String) : Command<BusResult<String, MessageFailure>>()

class ReturnCommandHandler : CommandHandler<ReturnCommand, BusResult<String, MessageFailure>>() {
    override val executeInTransaction: TransactionConfig? = null

    override suspend fun handle(message: ReturnCommand): BusResult<String, MessageFailure> {
        return success(message.messageData)
    }
}

open class StorageCommand(val messageData: String, val listStore: MutableList<String>) :
    Command<BusResult<Unit, MessageFailure>>()

class StorageCommandHandler : CommandHandler<StorageCommand, BusResult<Unit, MessageFailure>>() {
    override val executeInTransaction: TransactionConfig? = null

    override suspend fun handle(message: StorageCommand): BusResult<Unit, MessageFailure> {
        message.listStore.add(message.messageData)
        return success(Unit)
    }
}

class AnyCommandHandler :
    CommandHandler<Command<BusResult<Unit, MessageFailure>>, BusResult<Unit, MessageFailure>>() {
    override val executeInTransaction: TransactionConfig? = null

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

/** A second, distinct integration event so subscription sets have something to discriminate. */
open class OtherStorageEvent(val eventData: String, val listStore: MutableList<String>) :
    IntegrationEvent()

class OtherStorageEventHandler(private val label: String = "other") :
    EventHandler<OtherStorageEvent> {
    override suspend fun handle(message: OtherStorageEvent) {
        message.listStore.add("$label:${message.eventData}")
    }
}

class DelayingStorageEventHandler(private val delayMs: Long) : EventHandler<StorageEvent> {
    override suspend fun handle(message: StorageEvent) {
        delay(delayMs)
        message.listStore.add(message.eventData)
    }
}

class ThrowingStorageEventHandler : EventHandler<StorageEvent> {
    override suspend fun handle(message: StorageEvent) {
        throw TestHandlerException("Handler failed for: ${message.eventData}")
    }
}
