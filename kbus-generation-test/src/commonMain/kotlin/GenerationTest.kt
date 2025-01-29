package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.annotations.Load
import com.jimbroze.kbus.core.BusLocker
import com.jimbroze.kbus.core.BusResult
import com.jimbroze.kbus.core.Command
import com.jimbroze.kbus.core.CommandHandler
import com.jimbroze.kbus.core.FailureReason
import com.jimbroze.kbus.core.MessageBus
import com.jimbroze.kbus.core.Query
import com.jimbroze.kbus.core.QueryHandler
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FixedClock(private var fixedInstant: Instant) : Clock {
    override fun now(): Instant = fixedInstant

    fun travelTo(instant: Instant) {
        fixedInstant = instant
    }
}

class ClockFactory(private val clock: Clock) {
    fun createClock(): Clock {
        return clock
    }
}

class ClockFactoryHolder(
    private val clockFactory: ClockFactory,
    private val timeOverride: Instant? = null,
) {
    fun getClockFactory(): ClockFactory {
        return if (timeOverride != null) {
            ClockFactory(FixedClock(timeOverride))
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

class TestGeneratorCommand(val messageData: String) : Command()

@Load
class TestGeneratorCommandHandler(
    private val locker: BusLocker,
    private val clockFactoryHolder: ClockFactoryHolder,
) : CommandHandler<TestGeneratorCommand, Any, FailureReason>() {
    override suspend fun handle(message: TestGeneratorCommand): BusResult<Any, FailureReason> {
        val clock = clockFactoryHolder.getClockFactory().createClock()
        locker.toString()
        return success(message.messageData + clock.now().toString())
    }
}

class TestDuplicateGeneratorCommand(val messageData: String) : Command()

@Load
class TestDuplicateGeneratorCommandHandler(
    private val clockFactory: ClockFactory,
    private val bus: MessageBus,
    private val aString: TypeAliasString,
    private val stringCombiner: StringCombinator,
) : CommandHandler<TestDuplicateGeneratorCommand, Any, FailureReason>() {
    override suspend fun handle(
        message: TestDuplicateGeneratorCommand
    ): BusResult<Any, FailureReason> {
        val returnMessage =
            stringCombiner.combine(
                aString,
                clockFactory.createClock().now().toString(),
                bus.toString(),
            )

        return success(returnMessage)
    }
}

class TestGeneratorQuery(val messageData: String) : Query()

@Load
class TestGeneratorQueryHandler(private val locker: BusLocker, private val clock: Clock) :
    QueryHandler<TestGeneratorQuery, Any, FailureReason> {
    override suspend fun handle(message: TestGeneratorQuery): BusResult<Any, FailureReason> {
        locker.toString()
        return success(message.messageData + clock.now().toString())
    }
}
