package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.annotations.Load
import com.jimbroze.kbus.core.BusLocker
import com.jimbroze.kbus.core.BusResult
import com.jimbroze.kbus.core.Command
import com.jimbroze.kbus.core.CommandHandler
import com.jimbroze.kbus.core.ExecuteInTransaction
import com.jimbroze.kbus.core.MessageBus
import com.jimbroze.kbus.core.MessageFailure
import com.jimbroze.kbus.core.Query
import com.jimbroze.kbus.core.QueryHandler
import com.jimbroze.kbus.core.domain.DomainEventPublisher
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FixedClock(private var fixedInstant: Instant) : Clock {
    override fun now(): Instant = fixedInstant

    fun travelTo(instant: Instant) {
        fixedInstant = instant
    }
}

class ClockFactory(
    private val clock: Clock,
    private val domainEventPublisher: DomainEventPublisher,
) {
    fun createClock(): Clock {
        return clock
    }
}

class ClockFactoryHolder(
    private val clockFactory: ClockFactory,
    private val domainEventPublisher: DomainEventPublisher,
    private val timeOverride: Instant? = null,
) {
    fun getClockFactory(): ClockFactory {
        return if (timeOverride != null) {
            ClockFactory(FixedClock(timeOverride), domainEventPublisher)
        } else {
            clockFactory
        }
    }
}

class StringCombinator(
    private val stringCombinerOne: (String, String) -> String,
    private val stringCombinerTwo: (String, String) -> String,
) {
    fun combine(stringOne: String, stringTwo: String, stringThree: String): String {
        return stringCombinerTwo(stringCombinerOne(stringOne, stringTwo), stringThree)
    }
}

typealias TypeAliasString = String

class TestGeneratorCommand(val messageData: String) : Command<BusResult<Any, MessageFailure>>()

@Load
class TestGeneratorCommandHandler(
    private val locker: BusLocker,
    private val clockFactoryHolder: ClockFactoryHolder,
) :
    CommandHandler<TestGeneratorCommand, BusResult<Any, MessageFailure>>(),
    ExecuteInTransaction<TestGeneratorCommand, BusResult<Any, MessageFailure>> {
    override suspend fun handle(message: TestGeneratorCommand): BusResult<Any, MessageFailure> {
        val clock = clockFactoryHolder.getClockFactory().createClock()
        locker.toString()
        return BusResult.success(message.messageData + clock.now().toString())
    }
}

class TestDuplicateGeneratorCommand(val messageData: String?) :
    Command<BusResult<Any, MessageFailure>>()

@Load
class TestDuplicateGeneratorCommandHandler(
    private val clockFactory: ClockFactory,
    private val bus: MessageBus,
    private val aString: TypeAliasString,
    private val stringCombiner: StringCombinator,
) : CommandHandler<TestDuplicateGeneratorCommand, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(
        message: TestDuplicateGeneratorCommand
    ): BusResult<Any, MessageFailure> {
        val stringOne =
            if (message.messageData === null) {
                "Null message $aString"
            } else {
                message.messageData + aString
            }

        val returnMessage =
            stringCombiner.combine(
                stringOne,
                clockFactory.createClock().now().toString(),
                bus.middlewares.toString(),
            )

        return BusResult.success(returnMessage)
    }
}

class TestGeneratorQuery(val messageData: String, val moreMessageData: String) :
    Query<BusResult<Any, MessageFailure>>()

@Load
class TestGeneratorQueryHandler(private val locker: BusLocker, private val clock: Clock) :
    QueryHandler<TestGeneratorQuery, BusResult<Any, MessageFailure>> {
    override suspend fun handle(message: TestGeneratorQuery): BusResult<Any, MessageFailure> {
        locker.toString()
        return BusResult.success(
            message.messageData + message.moreMessageData + clock.now().toString()
        )
    }
}
