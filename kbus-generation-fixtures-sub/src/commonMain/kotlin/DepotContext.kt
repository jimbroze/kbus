package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.api.annotations.LoadEventMapper
import com.jimbroze.kbus.api.annotations.LoadMessageHandler
import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.api.messages.event.ErrorStrategy
import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import com.jimbroze.kbus.api.messages.event.IntegrationEventHandler
import com.jimbroze.kbus.api.messages.query.Query
import com.jimbroze.kbus.api.messages.query.QueryHandler
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.api.result.MessageFailure
import com.jimbroze.kbus.core.messages.event.dispatch.IntegrationEventMapper
import com.jimbroze.kbus.domain.event.DispatchTiming
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import com.jimbroze.kbus.domain.event.DomainEventPublisher
import com.jimbroze.kbus.generated.kbusGenerationFixturesSub.DepotCommands

class RecordArrival(val itemId: String) : Command<BusResult<String, MessageFailure>>()

class RecordArrivalAndRestock(val itemId: String) : Command<BusResult<String, MessageFailure>>()

class ArrivalCount(val itemId: String) : Query<BusResult<Int, MessageFailure>>()

class ArrivalRecorded(val itemId: String) : DomainEvent()

class ArrivalConfirmed(val itemId: String) : IntegrationEvent() {
    // FailFast dispatches handlers synchronously rather than fire-and-forget, so a test can assert
    // on the handler's effect without a race against a background coroutine.
    override val errorStrategy = ErrorStrategy.FailFast
}

@LoadEventMapper
object ArrivalConfirmedMapper : IntegrationEventMapper<ArrivalRecorded> {
    override fun fromDomainEvent(event: ArrivalRecorded) = ArrivalConfirmed(event.itemId)
}

interface ArrivalLog {
    suspend fun record(itemId: String)

    suspend fun countFor(itemId: String): Int
}

@LoadMessageHandler
@Suppress("unused")
class RecordArrivalHandler(
    private val arrivalLog: ArrivalLog,
    private val domainEventPublisher: DomainEventPublisher,
) : CommandHandler<RecordArrival, BusResult<String, MessageFailure>>() {
    override suspend fun handle(message: RecordArrival): BusResult<String, MessageFailure> {
        arrivalLog.record(message.itemId)
        domainEventPublisher.publish(ArrivalRecorded(message.itemId))
        return BusResult.success(message.itemId)
    }
}

/**
 * Reaching a sibling command through [DepotCommands] runs it inside this command's transaction and
 * event phases. The same command sent through the bus would get its own.
 */
@LoadMessageHandler
@Suppress("unused")
class RecordArrivalAndRestockHandler(private val depotCommands: DepotCommands) :
    CommandHandler<RecordArrivalAndRestock, BusResult<String, MessageFailure>>() {
    override suspend fun handle(
        message: RecordArrivalAndRestock
    ): BusResult<String, MessageFailure> =
        depotCommands.recordArrival(RecordArrival(message.itemId))
}

@LoadMessageHandler
@Suppress("unused")
class ArrivalCountHandler(private val arrivalLog: ArrivalLog) :
    QueryHandler<ArrivalCount, BusResult<Int, MessageFailure>>() {
    override suspend fun handle(message: ArrivalCount): BusResult<Int, MessageFailure> =
        BusResult.success(arrivalLog.countFor(message.itemId))
}

@LoadMessageHandler
@Suppress("unused")
class AuditArrivalHandler : DomainEventHandler<ArrivalRecorded>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: ArrivalRecorded) {
        auditedItemIds.add(message.itemId)
    }

    companion object {
        val auditedItemIds = mutableListOf<String>()
    }
}

@LoadMessageHandler
@Suppress("unused")
class ConfirmArrivalHandler : IntegrationEventHandler<ArrivalConfirmed> {
    override suspend fun handle(message: ArrivalConfirmed) {
        timesHandled++
    }

    companion object {
        var timesHandled = 0
    }
}
