package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.api.messages.event.Event
import com.jimbroze.kbus.api.messages.event.EventHandler
import com.jimbroze.kbus.api.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.api.messages.query.Query
import com.jimbroze.kbus.api.messages.query.QueryHandler
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.api.result.FailureReason
import com.jimbroze.kbus.api.result.MessageFailure
import com.jimbroze.kbus.api.uow.TransactionConfig

open class FailureCommand : Command<BusResult<String, FailureCommandFailure>>()

class BrokenStateFailureReason(override val message: String) : FailureReason

sealed interface FailureCommandFailure : MessageFailure {
    class BrokenStateFailure(message: String) : FailureCommandFailure {
        override val reason = BrokenStateFailureReason(message)
    }
}

class BrokenStateFailureCommandHandler :
    CommandHandler<FailureCommand, BusResult<String, FailureCommandFailure>>() {
    override val executeInTransaction: TransactionConfig? = null

    override suspend fun handle(message: FailureCommand): BusResult<String, FailureCommandFailure> {
        return BusResult.failure(
            FailureCommandFailure.BrokenStateFailure("Illegal state in command handling")
        )
    }
}

open class StorageQuery(val index: Int, val listStore: MutableList<String>) :
    Query<BusResult<String, MessageFailure>>()

class StorageQueryHandler : QueryHandler<StorageQuery, BusResult<String, MessageFailure>>() {
    override suspend fun handle(message: StorageQuery): BusResult<String, MessageFailure> {
        return BusResult.success(message.listStore[message.index])
    }
}

open class FailureQuery : Query<BusResult<String, FailureCommandFailure>>()

class FailureQueryHandler : QueryHandler<FailureQuery, BusResult<String, FailureCommandFailure>>() {
    override suspend fun handle(message: FailureQuery): BusResult<String, FailureCommandFailure> {
        return BusResult.failure(FailureCommandFailure.BrokenStateFailure("The query failed"))
    }
}

class TestEvent(val message: String) : Event()

class TestIntegrationEventHandler(val messageOutput: MutableList<String>) :
    EventHandler<TestEvent> {
    override suspend fun handle(message: TestEvent) {
        messageOutput.add(message.message)
    }
}

class EventCommand(val message: String, val listStore: MutableList<String>) :
    Command<BusResult<Unit, MessageFailure>>()

class EventCommandHandler(private val integrationEventPublisher: IntegrationEventPublisher) :
    CommandHandler<EventCommand, BusResult<Unit, MessageFailure>>() {
    override val executeInTransaction: TransactionConfig? = null

    override suspend fun handle(message: EventCommand): BusResult<Unit, MessageFailure> {
        integrationEventPublisher.publish(listOf(StorageEvent(message.message, message.listStore)))
        return BusResult.success(Unit)
    }
}
