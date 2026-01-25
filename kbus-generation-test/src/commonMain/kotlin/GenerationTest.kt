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
}

@Suppress("unused")
class RequiresCommandDepsContainsInterface(
    private val clock: Clock,
    private val domainEventPublisher: DomainEventPublisher,
) {
    fun createClock(): Clock {
        return clock
    }
}

class RequiresCommandDepsContainsPrimitive(
    val requiresCommandDepsContainsInterface: RequiresCommandDepsContainsInterface,
    val instant: Instant,
)

class ContainsInterface(val clock: Clock)

@Suppress("unused") class ContainsString(private val aString: String)

typealias TypeAliasStringOne = String

typealias TypeAliasStringTwo = String

typealias TypeAliasStringCombiner = (String, String) -> String

class GenericClass<T>(val data: T)

object AnObject

@Suppress("unused")
class ContainsFunction(private val stringCombinerOne: (String, String) -> String)

@Suppress("unused")
class ContainsTypeAliases(
    private val aliasString: TypeAliasStringOne,
    private val aliasStringTwo: TypeAliasStringTwo,
    private val aliasFunction: TypeAliasStringCombiner,
)

class NestedClassesCommand(val messageData: String) : Command<BusResult<Any, MessageFailure>>()

// TODO organise deps into handlers
@LoadMessageHandler
@Suppress("unused")
class NestedClassesCommandHandler(
    private val functionalContainsPrimitive: RequiresCommandDepsContainsPrimitive,
    private val containsString: ContainsString,
    private val containsFunction: ContainsFunction,
    private val containsTypeAliases: ContainsTypeAliases,
    private val containsInterface: ContainsInterface,
    private val requiresCommandDepsContainsInterface: RequiresCommandDepsContainsInterface,
) :
    CommandHandler<NestedClassesCommand, BusResult<Any, MessageFailure>>(),
    ExecuteInTransaction<NestedClassesCommand, BusResult<Any, MessageFailure>> {
    override suspend fun handle(message: NestedClassesCommand): BusResult<Any, MessageFailure> {
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
        return BusResult.success("success")
    }
}

class OtherClassesCommand(val messageData: String?) : Command<BusResult<Any, MessageFailure>>()

@Suppress("unused")
@LoadMessageHandler
class OtherClassesCommandHandler(private val locker: BusLocker, private val bus: MessageBus) :
    CommandHandler<OtherClassesCommand, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(message: OtherClassesCommand): BusResult<Any, MessageFailure> {
        return BusResult.success("success")
    }
}

class InterfacesCommand(val messageData: String?) : Command<BusResult<Any, MessageFailure>>()

@Suppress("unused")
@LoadMessageHandler
class InterfacesCommandHandler(private val bus: BaseMessageBus, private val clock: Clock) :
    CommandHandler<InterfacesCommand, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(message: InterfacesCommand): BusResult<Any, MessageFailure> {
        return BusResult.success("success")
    }
}

class NonClassTypesCommand(val messageData: String?) : Command<BusResult<Any, MessageFailure>>()

@Suppress("unused")
@LoadMessageHandler
class NonClassTypesCommandHandler(
    private val stringTypeAlias: TypeAliasStringOne,
    private val stringCombiner: TypeAliasStringCombiner,
    private val anObject: AnObject,
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
    private val containsFunction: ContainsFunction,
) : QueryHandler<TestGeneratorQuery, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(message: TestGeneratorQuery): BusResult<Any, MessageFailure> {
        locker.toString()
        return BusResult.success(
            message.messageData + message.moreMessageData + clock.now().toString()
        )
    }
}
