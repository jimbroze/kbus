package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.annotations.LoadMessageHandler
import com.jimbroze.kbus.core.bus.BaseMessageBus
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

@Suppress("unused") class ContainsString(private val aString: String)

typealias TypeAliasString = String

typealias TypeAliasStringCombiner = (String, String) -> String

@Suppress("unused")
class ContainsFunctions(
    private val stringCombinerOne: (String, String) -> String,
    private val stringCombinerTwo: TypeAliasStringCombiner,
)

@Suppress("unused")
class ContainsTypeAliases(
    private val aliasString: TypeAliasString,
    private val aliasFunction: TypeAliasStringCombiner,
)

class TestGeneratorCommand(val messageData: String) : Command<BusResult<Any, MessageFailure>>()

class GenericClass<T>(val data: T)

// TODO organise deps into handlers
@LoadMessageHandler
@Suppress("unused")
class NestedClassesCommandHandler(
    private val functionalContainsInstant: RequiresCommandDepsContainsInstant,
    private val containsString: ContainsString,
    private val containsFunctions: ContainsFunctions,
    private val containsTypeAliases: ContainsTypeAliases,
    private val requiresCommandDepsContainsClock: RequiresCommandDepsContainsClock,
) :
    CommandHandler<TestGeneratorCommand, BusResult<Any, MessageFailure>>(),
    ExecuteInTransaction<TestGeneratorCommand, BusResult<Any, MessageFailure>> {
    override suspend fun handle(message: TestGeneratorCommand): BusResult<Any, MessageFailure> {
        return BusResult.success("success")
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

class OtherClassesCommandCommand(val messageData: String?) :
    Command<BusResult<Any, MessageFailure>>()

@Suppress("unused")
@LoadMessageHandler
class OtherClassesCommandHandler(private val locker: BusLocker, private val bus: MessageBus) :
    CommandHandler<OtherClassesCommandCommand, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(
        message: OtherClassesCommandCommand
    ): BusResult<Any, MessageFailure> {
        return BusResult.success("success")
    }
}

class InterfacesCommandCommand(val messageData: String?) :
    Command<BusResult<Any, MessageFailure>>()

@Suppress("unused")
@LoadMessageHandler
class InterfacesCommandHandler(private val bus: BaseMessageBus, private val clock: Clock) :
    CommandHandler<InterfacesCommandCommand, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(message: InterfacesCommandCommand): BusResult<Any, MessageFailure> {
        return BusResult.success("success")
    }
}

class NonClassTypesCommand(val messageData: String?) : Command<BusResult<Any, MessageFailure>>()

@Suppress("unused")
@LoadMessageHandler
class NonClassTypesCommandHandler(
    private val stringTypeAlias: TypeAliasString,
    private val stringCombiner: TypeAliasStringCombiner,
) : CommandHandler<NonClassTypesCommand, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(message: NonClassTypesCommand): BusResult<Any, MessageFailure> {
        return BusResult.success("success")
    }
}

class TestGeneratorQuery(val messageData: String, val moreMessageData: String) :
    Query<BusResult<Any, MessageFailure>>()

@Suppress("unused")
@LoadMessageHandler
class TestGeneratorQueryHandler(
    private val locker: BusLocker,
    private val clock: Clock,
    private val genericClassString: GenericClass<String>,
    private val stringCombiner: TypeAliasStringCombiner,
    private val containsFunctions: ContainsFunctions,
) : QueryHandler<TestGeneratorQuery, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(message: TestGeneratorQuery): BusResult<Any, MessageFailure> {
        locker.toString()
        return BusResult.success(
            message.messageData + message.moreMessageData + clock.now().toString()
        )
    }
}
