package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.contracts.messages.event.CanPublishIntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.EventDestination
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.core.messages.EventHandlerDependencies
import com.jimbroze.kbus.domain.event.Concurrency
import com.jimbroze.kbus.domain.event.DispatchTiming
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import com.jimbroze.kbus.domain.event.DomainEventPublisher
import com.jimbroze.kbus.domain.event.ErrorStrategy
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

// --- Integration event publisher ---

object EmptyIntegrationEventPublisher : IntegrationEventPublisher {
    override suspend fun publish(events: List<IntegrationEvent>) = Unit
}

/** For handlers under test that never publish. */
val noPublishHandlerDependencies = EventHandlerDependencies(EmptyIntegrationEventPublisher)

class RecordingIntegrationEventPublisher : IntegrationEventPublisher {
    val publishedEvents = mutableListOf<List<IntegrationEvent>>()

    override suspend fun publish(events: List<IntegrationEvent>) {
        publishedEvents.add(events)
    }
}

// --- Event destinations / router ---

/**
 * Records delivered envelopes, one entry per [deliver] call. Accepts everything unless [failure] is
 * set. [deliveredCalls] additionally records each [deliver] invocation as its own list, so a caller
 * that delivers one envelope at a time (an inbox) can be distinguished from one that batches.
 * [failureFor] fails individual envelopes rather than the whole call; [beforeDeliver] is a hook run
 * before every call, useful for gating concurrent deliveries in tests.
 */
class RecordingDestination(override val name: String = "recording") : EventDestination {
    val delivered = mutableListOf<EventEnvelope>()
    val deliveredCalls = mutableListOf<List<EventEnvelope>>()
    var failure: Throwable? = null
    var failureFor: ((EventEnvelope) -> Throwable?)? = null
    var beforeDeliver: (suspend () -> Unit)? = null

    override fun appliesTo(event: IntegrationEvent): Boolean = true

    override suspend fun deliver(envelopes: List<EventEnvelope>) {
        beforeDeliver?.invoke()
        failure?.let { throw it }
        envelopes.forEach { envelope -> failureFor?.invoke(envelope)?.let { throw it } }
        delivered.addAll(envelopes)
        deliveredCalls.add(envelopes)
    }
}

// --- Domain events ---

class TestDomainEvent(val data: String) : DomainEvent()

class TestSequentialDomainEvent(val data: String) : DomainEvent() {
    override val concurrency = Concurrency.Sequential
}

// --- Error strategy events ---

class TestFailFastEvent(val data: String) : DomainEvent() {
    override val errorStrategy = ErrorStrategy.FailFast
}

class TestFireAndForgetEvent(val data: String) : DomainEvent() {
    override val errorStrategy = ErrorStrategy.FireAndForget
}

class TestContinueAndAggregateEvent(val data: String) : DomainEvent() {
    override val errorStrategy = ErrorStrategy.ContinueAndAggregate
}

// --- Error strategy + sequential concurrency events ---

class TestSequentialFailFastEvent(val data: String) : DomainEvent() {
    override val concurrency = Concurrency.Sequential
    override val errorStrategy = ErrorStrategy.FailFast
}

class TestSequentialFireAndForgetEvent(val data: String) : DomainEvent() {
    override val concurrency = Concurrency.Sequential
    override val errorStrategy = ErrorStrategy.FireAndForget
}

class TestSequentialContinueAndAggregateEvent(val data: String) : DomainEvent() {
    override val concurrency = Concurrency.Sequential
    override val errorStrategy = ErrorStrategy.ContinueAndAggregate
}

// --- Basic domain event handlers ---

class TestDomainEventHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestDomainEvent>() {
    override suspend fun handle(message: TestDomainEvent) {
        results.add(message.data)
    }
}

/** Publishes an integration event via the [DomainEventHandler] mixin, synchronously. */
class PublishingDomainEventHandler : DomainEventHandler<TestDomainEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestDomainEvent) {
        publish(TestIntegrationEvent(message.data))
    }
}

class TestDispatchAtEndOfTransactionHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestDomainEvent>() {
    override val dispatchTiming = DispatchTiming.AtEndOfTransaction

    override suspend fun handle(message: TestDomainEvent) {
        results.add(message.data)
    }
}

class TestDispatchAfterTransactionHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestDomainEvent>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: TestDomainEvent) {
        results.add(message.data)
    }
}

class TestDomainEventPublisher : DomainEventPublisher {
    val publishedEvents = mutableListOf<DomainEvent>()

    override suspend fun publish(event: DomainEvent) {
        publishedEvents.add(event)
    }
}

class TestDispatchImmediatelyHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestDomainEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestDomainEvent) {
        results.add(message.data)
    }
}

// --- Delaying handlers for TestDomainEvent (concurrent by default) ---

class DelayingDomainEventHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DomainEventHandler<TestDomainEvent>() {
    override suspend fun handle(message: TestDomainEvent) {
        delay(delayMs.milliseconds)
        results.add(label)
    }
}

class DelayingDispatchImmediatelyHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DomainEventHandler<TestDomainEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestDomainEvent) {
        delay(delayMs.milliseconds)
        results.add(label)
    }
}

class DelayingDispatchAtEndOfTransactionHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DomainEventHandler<TestDomainEvent>() {
    override val dispatchTiming = DispatchTiming.AtEndOfTransaction

    override suspend fun handle(message: TestDomainEvent) {
        delay(delayMs.milliseconds)
        results.add(label)
    }
}

class DelayingDispatchAfterTransactionHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DomainEventHandler<TestDomainEvent>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: TestDomainEvent) {
        delay(delayMs.milliseconds)
        results.add(label)
    }
}

// --- Delaying handlers for TestSequentialDomainEvent ---

class DelayingSequentialDomainEventHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DomainEventHandler<TestSequentialDomainEvent>() {
    override suspend fun handle(message: TestSequentialDomainEvent) {
        delay(delayMs.milliseconds)
        results.add(label)
    }
}

class DelayingSequentialImmediateHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DomainEventHandler<TestSequentialDomainEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestSequentialDomainEvent) {
        delay(delayMs.milliseconds)
        results.add(label)
    }
}

class DelayingSequentialEndOfTransactionHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DomainEventHandler<TestSequentialDomainEvent>() {
    override val dispatchTiming = DispatchTiming.AtEndOfTransaction

    override suspend fun handle(message: TestSequentialDomainEvent) {
        delay(delayMs.milliseconds)
        results.add(label)
    }
}

class DelayingSequentialAfterTransactionHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DomainEventHandler<TestSequentialDomainEvent>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: TestSequentialDomainEvent) {
        delay(delayMs.milliseconds)
        results.add(label)
    }
}

// --- Throwing/succeeding handlers for error strategy tests ---

class ThrowingDomainEventHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestDomainEvent>() {
    override suspend fun handle(message: TestDomainEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("Handler failed for: ${message.data}")
    }
}

class ThrowingDispatchImmediatelyHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestDomainEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestDomainEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("Handler failed for: ${message.data}")
    }
}

// --- FailFast handlers (per dispatch phase) ---

class ThrowingFailFastHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestFailFastEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestFailFastEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("FailFast handler failed for: ${message.data}")
    }
}

class SucceedingFailFastHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestFailFastEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestFailFastEvent) {
        results.add("success:${message.data}")
    }
}

class ThrowingFailFastAtEndOfTransactionHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestFailFastEvent>() {
    override val dispatchTiming = DispatchTiming.AtEndOfTransaction

    override suspend fun handle(message: TestFailFastEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("FailFast handler failed for: ${message.data}")
    }
}

class SucceedingFailFastAtEndOfTransactionHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestFailFastEvent>() {
    override val dispatchTiming = DispatchTiming.AtEndOfTransaction

    override suspend fun handle(message: TestFailFastEvent) {
        results.add("success:${message.data}")
    }
}

class ThrowingFailFastAfterTransactionHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestFailFastEvent>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: TestFailFastEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("FailFast handler failed for: ${message.data}")
    }
}

class SucceedingFailFastAfterTransactionHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestFailFastEvent>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: TestFailFastEvent) {
        results.add("success:${message.data}")
    }
}

class DefaultPhaseFailFastHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestFailFastEvent>() {
    override suspend fun handle(message: TestFailFastEvent) {
        results.add("success:${message.data}")
    }
}

// --- FireAndForget handlers (per dispatch phase) ---

class ThrowingFireAndForgetHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestFireAndForgetEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestFireAndForgetEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("FireAndForget handler failed for: ${message.data}")
    }
}

class SucceedingFireAndForgetHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestFireAndForgetEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestFireAndForgetEvent) {
        results.add("success:${message.data}")
    }
}

class ThrowingFireAndForgetAtEndOfTransactionHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestFireAndForgetEvent>() {
    override val dispatchTiming = DispatchTiming.AtEndOfTransaction

    override suspend fun handle(message: TestFireAndForgetEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("FireAndForget handler failed for: ${message.data}")
    }
}

class SucceedingFireAndForgetAtEndOfTransactionHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestFireAndForgetEvent>() {
    override val dispatchTiming = DispatchTiming.AtEndOfTransaction

    override suspend fun handle(message: TestFireAndForgetEvent) {
        results.add("success:${message.data}")
    }
}

class ThrowingFireAndForgetAfterTransactionHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestFireAndForgetEvent>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: TestFireAndForgetEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("FireAndForget handler failed for: ${message.data}")
    }
}

class SucceedingFireAndForgetAfterTransactionHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestFireAndForgetEvent>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: TestFireAndForgetEvent) {
        results.add("success:${message.data}")
    }
}

class DelayingFireAndForgetAfterTransactionHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DomainEventHandler<TestFireAndForgetEvent>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: TestFireAndForgetEvent) {
        delay(delayMs.milliseconds)
        results.add(label)
    }
}

// --- ContinueAndAggregate handlers (per dispatch phase) ---

class ThrowingContinueAndAggregateHandler(
    private val results: MutableList<String>,
    private val label: String,
) : DomainEventHandler<TestContinueAndAggregateEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestContinueAndAggregateEvent) {
        results.add("threw:$label")
        throw TestHandlerException("ContinueAndAggregate handler '$label' failed")
    }
}

class SucceedingContinueAndAggregateHandler(
    private val results: MutableList<String>,
    private val label: String,
) : DomainEventHandler<TestContinueAndAggregateEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestContinueAndAggregateEvent) {
        results.add("success:$label")
    }
}

/** Throws after [delayMs], so a fast, non-delaying, later-indexed handler can finish first. */
class DelayingThrowingContinueAndAggregateHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DomainEventHandler<TestContinueAndAggregateEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestContinueAndAggregateEvent) {
        delay(delayMs.milliseconds)
        results.add("threw:$label")
        throw TestHandlerException("ContinueAndAggregate handler '$label' failed")
    }
}

class ThrowingContinueAndAggregateAtEndOfTransactionHandler(
    private val results: MutableList<String>,
    private val label: String,
) : DomainEventHandler<TestContinueAndAggregateEvent>() {
    override val dispatchTiming = DispatchTiming.AtEndOfTransaction

    override suspend fun handle(message: TestContinueAndAggregateEvent) {
        results.add("threw:$label")
        throw TestHandlerException("ContinueAndAggregate handler '$label' failed")
    }
}

class SucceedingContinueAndAggregateAtEndOfTransactionHandler(
    private val results: MutableList<String>,
    private val label: String,
) : DomainEventHandler<TestContinueAndAggregateEvent>() {
    override val dispatchTiming = DispatchTiming.AtEndOfTransaction

    override suspend fun handle(message: TestContinueAndAggregateEvent) {
        results.add("success:$label")
    }
}

class ThrowingContinueAndAggregateAfterTransactionHandler(
    private val results: MutableList<String>,
    private val label: String,
) : DomainEventHandler<TestContinueAndAggregateEvent>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: TestContinueAndAggregateEvent) {
        results.add("threw:$label")
        throw TestHandlerException("ContinueAndAggregate handler '$label' failed")
    }
}

class SucceedingContinueAndAggregateAfterTransactionHandler(
    private val results: MutableList<String>,
    private val label: String,
) : DomainEventHandler<TestContinueAndAggregateEvent>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: TestContinueAndAggregateEvent) {
        results.add("success:$label")
    }
}

// --- Sequential FailFast handlers (for concurrency x error strategy orthogonality) ---

class ThrowingSequentialFailFastHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestSequentialFailFastEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestSequentialFailFastEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("FailFast handler failed for: ${message.data}")
    }
}

class SucceedingSequentialFailFastHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestSequentialFailFastEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestSequentialFailFastEvent) {
        results.add("success:${message.data}")
    }
}

// --- Sequential ContinueAndAggregate handlers ---

class ThrowingSequentialContinueAndAggregateHandler(
    private val results: MutableList<String>,
    private val label: String,
) : DomainEventHandler<TestSequentialContinueAndAggregateEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestSequentialContinueAndAggregateEvent) {
        results.add("threw:$label")
        throw TestHandlerException("ContinueAndAggregate handler '$label' failed")
    }
}

class SucceedingSequentialContinueAndAggregateHandler(
    private val results: MutableList<String>,
    private val label: String,
) : DomainEventHandler<TestSequentialContinueAndAggregateEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestSequentialContinueAndAggregateEvent) {
        results.add("success:$label")
    }
}

// --- Sequential FireAndForget handlers ---

class ThrowingSequentialFireAndForgetHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestSequentialFireAndForgetEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestSequentialFireAndForgetEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("FireAndForget handler failed for: ${message.data}")
    }
}

class SucceedingSequentialFireAndForgetHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestSequentialFireAndForgetEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: TestSequentialFireAndForgetEvent) {
        results.add("success:${message.data}")
    }
}

// --- Integration event fixtures ---

class TestIntegrationEvent(val name: String) : IntegrationEvent()

class DelayingIntegrationEventHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : IntegrationEventHandler<TestIntegrationEvent> {
    override suspend fun handle(message: TestIntegrationEvent) {
        delay(delayMs.milliseconds)
        results.add(label)
    }
}

/** Publishes an integration event via the [CanPublishIntegrationEvent] mixin. */
class PublishingIntegrationEventHandler :
    CanPublishIntegrationEvent(), IntegrationEventHandler<TestIntegrationEvent> {
    override suspend fun handle(message: TestIntegrationEvent) {
        publish(TestIntegrationEvent("published-by-${message.name}"))
    }
}

class ThrowingIntegrationEventHandler(private val attempts: MutableList<String>) :
    IntegrationEventHandler<TestIntegrationEvent> {
    override suspend fun handle(message: TestIntegrationEvent) {
        attempts.add(message.name)
        throw TestHandlerException("Integration handler failed for: ${message.name}")
    }
}

class TestHandlerException(message: String) : Exception(message)
