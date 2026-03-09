package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.contracts.bus.BusAccess
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.FailureReason
import com.jimbroze.kbus.contracts.result.MessageFailure

class TestBusAccess : BusAccess {
    val dispatchedEvents = mutableListOf<Event>()

    override suspend fun <TEvent : Event> dispatch(event: TEvent) {
        dispatchedEvents.add(event)
    }
}

open class FailureCommand : Command<BusResult<String, FailureCommandFailure>>()

class BrokenStateFailureReason(override val message: String) : FailureReason

sealed interface FailureCommandFailure : MessageFailure {
    class BrokenStateFailure(message: String) : FailureCommandFailure {
        override val reason = BrokenStateFailureReason(message)
    }
}

class BrokenStateFailureCommandHandler :
    CommandHandler<FailureCommand, BusResult<String, FailureCommandFailure>>() {
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

class EventCommandHandler : CommandHandler<EventCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: EventCommand): BusResult<Unit, MessageFailure> {
        dispatch(StorageEvent(message.message, message.listStore))
        return BusResult.success(Unit)
    }
}
