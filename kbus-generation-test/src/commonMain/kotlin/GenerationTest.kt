package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.annotations.LoadMessageHandler
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.domain.DomainEventPublisher
import com.jimbroze.kbus.core.messages.command.Command
import com.jimbroze.kbus.core.messages.command.CommandHandler
import com.jimbroze.kbus.core.messages.query.Query
import com.jimbroze.kbus.core.messages.query.QueryHandler
import com.jimbroze.kbus.core.middleware.middleware.BusLocker
import com.jimbroze.kbus.core.result.BusResult
import com.jimbroze.kbus.core.result.MessageFailure
import com.jimbroze.kbus.core.uow.ExecuteInTransaction
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FixedClock(private var fixedInstant: Instant) : Clock {
    override fun now(): Instant = fixedInstant

    fun travelTo(instant: Instant) {
        fixedInstant = instant
    }
}

@Suppress("unused")
class RequiresCommandDepsContainsClock(
    private val clock: Clock,
    private val domainEventPublisher: DomainEventPublisher,
) {
    fun createClock(): Clock {
        return clock
    }
}

class RequiresCommandDepsContainsInstant(
    val requiresCommandDepsContainsClock: RequiresCommandDepsContainsClock,
    private val now: Instant? = null,
)

class ContainsString(private val aString: String)

class ContainsFunctions(
    private val stringCombinerOne: (String, String) -> String,
    private val stringCombinerTwo: (String, String) -> String,
) {
    fun combine(stringOne: String, stringTwo: String, stringThree: String): String {
        return stringCombinerTwo(stringCombinerOne(stringOne, stringTwo), stringThree)
    }
}

typealias TypeAliasString = String

class TestGeneratorCommand(val messageData: String) : Command<BusResult<Any, MessageFailure>>()

class GenericClass<T>(val data: T)

@LoadMessageHandler
@Suppress("unused")
class TestGeneratorCommandHandler(
    private val locker: BusLocker,
    private val functionalContainsInstant: RequiresCommandDepsContainsInstant,
    private val containsString: ContainsString,
    //    private val genericClassString: GenericClass<String>, //TODO re-add this
) :
    CommandHandler<TestGeneratorCommand, BusResult<Any, MessageFailure>>(),
    ExecuteInTransaction<TestGeneratorCommand, BusResult<Any, MessageFailure>> {
    override suspend fun handle(message: TestGeneratorCommand): BusResult<Any, MessageFailure> {
        val clock = functionalContainsInstant.requiresCommandDepsContainsClock.createClock()
        locker.toString()
        return BusResult.success(message.messageData + clock.now().toString())
    }
}

class GenericClassCommand(val messageData: String) : Command<BusResult<Any, MessageFailure>>()

@LoadMessageHandler
@Suppress("unused")
class GenericClassCommandHandler(
    private val genericClassString: GenericClass<String>,
    private val genericClassListString: GenericClass<List<String>>,
    private val genericClassGenericClassString: GenericClass<GenericClass<String>>,
) :
    CommandHandler<GenericClassCommand, BusResult<Any, MessageFailure>>(),
    ExecuteInTransaction<GenericClassCommand, BusResult<Any, MessageFailure>> {
    override suspend fun handle(message: GenericClassCommand): BusResult<Any, MessageFailure> {
        return BusResult.success(message.messageData)
    }
}

class TestDuplicateGeneratorCommand(val messageData: String?) :
    Command<BusResult<Any, MessageFailure>>()

@Suppress("unused")
@LoadMessageHandler
class TestDuplicateGeneratorCommandHandler(
    private val requiresCommandDepsContainsClock: RequiresCommandDepsContainsClock,
    private val bus: MessageBus,
    private val aString: TypeAliasString,
    private val stringCombiner: ContainsFunctions,
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
                requiresCommandDepsContainsClock.createClock().now().toString(),
                "[]",
            )

        return BusResult.success(returnMessage)
    }
}

class TestGeneratorQuery(val messageData: String, val moreMessageData: String) :
    Query<BusResult<Any, MessageFailure>>()

@LoadMessageHandler
class TestGeneratorQueryHandler(private val locker: BusLocker, private val clock: Clock) :
    QueryHandler<TestGeneratorQuery, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(message: TestGeneratorQuery): BusResult<Any, MessageFailure> {
        locker.toString()
        return BusResult.success(
            message.messageData + message.moreMessageData + clock.now().toString()
        )
    }
}
