@file:OptIn(ExperimentalTime::class)

package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.core.bus.BaseMessageBus
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.middleware.middleware.LockingMiddleware
import com.jimbroze.kbus.domain.event.DispatchTiming
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import com.jimbroze.kbus.domain.event.DomainEventPublisher
import com.test.external.ExternalEmpty
import com.test.external.ExternalInterface
import com.test.external.ExternalNestedWithExternal
import com.test.external.ExternalNestedWithPrimitive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class FixedClock(private var fixedInstant: Instant) : Clock {
    override fun now(): Instant = fixedInstant
}

@Suppress("unused")
class RequiresCommandDepsContainsInterface(
    private val clock: Clock,
    val domainEventPublisher: DomainEventPublisher,
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

class ContainsString(val aString: String)

class ContainsExternalEmpty(val externalDependency: ExternalEmpty)

class ContainsExternalNestedExternal(val externalDependency: ExternalNestedWithExternal)

class ContainsExternalNestedPrimitive(val externalDependency: ExternalNestedWithPrimitive)

typealias TypeAliasStringOne = String

typealias TypeAliasStringTwo = String

typealias TypeAliasStringCombiner = (String, String) -> String

class GenericClass<T>(val data: T)

object AnObject

typealias TransientExample = Clock

typealias LazySingletonExample = Clock

typealias EagerSingletonExample = Clock

@Suppress("unused")
class ContainsFunction(private val stringCombinerOne: (String, String) -> String)

@Suppress("unused")
class ContainsTypeAliases(
    private val aliasString: TypeAliasStringOne,
    private val aliasStringTwo: TypeAliasStringTwo,
    private val aliasFunction: TypeAliasStringCombiner,
)

class NestedClassesCommand(val messageData: String) : Command<BusResult<Any, MessageFailure>>()

@LoadMessageHandler
@Suppress("unused")
class NestedClassesCommandHandler(
    private val functionalContainsPrimitive: RequiresCommandDepsContainsPrimitive,
    private val containsString: ContainsString,
    private val containsFunction: ContainsFunction,
    private val containsTypeAliases: ContainsTypeAliases,
    private val containsInterface: ContainsInterface,
    private val requiresCommandDepsContainsInterface: RequiresCommandDepsContainsInterface,
) : CommandHandler<NestedClassesCommand, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(message: NestedClassesCommand): BusResult<Any, MessageFailure> {
        return BusResult.success("success")
    }
}

class ExternalDependenciesCommand(val messageData: String) :
    Command<BusResult<Any, MessageFailure>>()

@LoadMessageHandler
@Suppress("unused")
class ExternalDependenciesCommandHandler(
    private val externalInterface: ExternalInterface,
    private val containsExternalEmpty: ContainsExternalEmpty,
    private val containsExternalNestedPrimitive: ContainsExternalNestedPrimitive,
    private val containsExternalNestedExternal: ContainsExternalNestedExternal,
) : CommandHandler<ExternalDependenciesCommand, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(
        message: ExternalDependenciesCommand
    ): BusResult<Any, MessageFailure> {
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
) : CommandHandler<GenericClassCommand, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(message: GenericClassCommand): BusResult<Any, MessageFailure> {
        return BusResult.success("success")
    }
}

class OtherClassesCommand(val messageData: String?) : Command<BusResult<Any, MessageFailure>>()

@Suppress("unused")
@LoadMessageHandler
class OtherClassesCommandHandler(
    private val locker: LockingMiddleware,
    private val bus: MessageBus,
) : CommandHandler<OtherClassesCommand, BusResult<Any, MessageFailure>>() {
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

data class LifeCycleResult(
    val transientTime: Instant,
    val lazySingletonTime: Instant,
    val eagerSingletonTime: Instant,
)

class LifeCycleTestCommand : Command<BusResult<LifeCycleResult, MessageFailure>>()

@Suppress("unused")
@LoadMessageHandler
class LifeCycleTestCommandHandler(
    private val transientClock: TransientExample,
    private val lazySingletonClock: LazySingletonExample,
    private val eagerSingletonClock: EagerSingletonExample,
) : CommandHandler<LifeCycleTestCommand, BusResult<LifeCycleResult, MessageFailure>>() {
    override suspend fun handle(
        message: LifeCycleTestCommand
    ): BusResult<LifeCycleResult, MessageFailure> {
        val transientTime = transientClock.now()
        val lazySingletonTime = lazySingletonClock.now()
        val eagerSingletonTime = eagerSingletonClock.now()

        return BusResult.success(
            LifeCycleResult(transientTime, lazySingletonTime, eagerSingletonTime)
        )
    }
}

class TestGeneratorQuery(val messageData: String, val moreMessageData: String) :
    Query<BusResult<Any, MessageFailure>>()

@Suppress("unused")
@LoadMessageHandler
class TestGeneratorQueryHandler(
    private val locker: LockingMiddleware,
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

class TestGeneratorEvent : DomainEvent()

@LoadMessageHandler
@Suppress("unused")
class TestGeneratorEventHandler(@Suppress("unused") private val clock: Clock) :
    DomainEventHandler<TestGeneratorEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestGeneratorEvent) {
        timesHandled++
    }

    companion object {
        var timesHandled = 0
    }
}

class TestEventPublishingCommand : Command<BusResult<Any, MessageFailure>>()

@LoadMessageHandler
@Suppress("unused")
class TestEventPublishingCommandHandler(
    private val requiresCommandDepsContainsInterface: RequiresCommandDepsContainsInterface
) : CommandHandler<TestEventPublishingCommand, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(
        message: TestEventPublishingCommand
    ): BusResult<Any, MessageFailure> {
        requiresCommandDepsContainsInterface.domainEventPublisher.publish(TestGeneratorEvent())
        return BusResult.success("published")
    }
}
