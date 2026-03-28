package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventHandler
import com.jimbroze.kbus.core.messages.event.ContinueAndAggregateDomainEvent
import com.jimbroze.kbus.core.messages.event.DispatchAfterTransaction
import com.jimbroze.kbus.core.messages.event.DispatchAtEndOfTransaction
import com.jimbroze.kbus.core.messages.event.DispatchImmediatelyInTransaction
import com.jimbroze.kbus.core.messages.event.DispatchSequentially
import com.jimbroze.kbus.core.messages.event.DomainEventHandler
import com.jimbroze.kbus.core.messages.event.FailFastDomainEvent
import com.jimbroze.kbus.core.messages.event.FireAndForgetDomainEvent
import com.jimbroze.kbus.domain.DomainEvent
import com.jimbroze.kbus.domain.DomainEventPublisher
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

// --- Domain events ---

class TestDomainEvent(val data: String) : DomainEvent()

class TestSequentialDomainEvent(val data: String) : DomainEvent(), DispatchSequentially

// --- Error strategy events ---

class TestFailFastEvent(val data: String) : FailFastDomainEvent()

class TestFireAndForgetEvent(val data: String) : FireAndForgetDomainEvent()

class TestContinueAndAggregateEvent(val data: String) : ContinueAndAggregateDomainEvent()

// --- Error strategy + sequential concurrency events ---

class TestSequentialFailFastEvent(val data: String) : FailFastDomainEvent(), DispatchSequentially

class TestSequentialFireAndForgetEvent(val data: String) :
    FireAndForgetDomainEvent(), DispatchSequentially

class TestSequentialContinueAndAggregateEvent(val data: String) :
    ContinueAndAggregateDomainEvent(), DispatchSequentially

// --- Basic domain event handlers ---

class TestDomainEventHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestDomainEvent>() {
    override suspend fun handle(message: TestDomainEvent) {
        results.add(message.data)
    }
}

class TestDispatchAtEndOfTransactionHandler(private val results: MutableList<String>) :
    DispatchAtEndOfTransaction<TestDomainEvent>() {
    override suspend fun handle(message: TestDomainEvent) {
        results.add(message.data)
    }
}

class TestDispatchAfterTransactionHandler(private val results: MutableList<String>) :
    DispatchAfterTransaction<TestDomainEvent>() {
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
    DispatchImmediatelyInTransaction<TestDomainEvent>() {
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
) : DispatchImmediatelyInTransaction<TestDomainEvent>() {
    override suspend fun handle(message: TestDomainEvent) {
        delay(delayMs.milliseconds)
        results.add(label)
    }
}

class DelayingDispatchAtEndOfTransactionHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DispatchAtEndOfTransaction<TestDomainEvent>() {
    override suspend fun handle(message: TestDomainEvent) {
        delay(delayMs.milliseconds)
        results.add(label)
    }
}

class DelayingDispatchAfterTransactionHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DispatchAfterTransaction<TestDomainEvent>() {
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
) : DispatchImmediatelyInTransaction<TestSequentialDomainEvent>() {
    override suspend fun handle(message: TestSequentialDomainEvent) {
        delay(delayMs.milliseconds)
        results.add(label)
    }
}

class DelayingSequentialEndOfTransactionHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DispatchAtEndOfTransaction<TestSequentialDomainEvent>() {
    override suspend fun handle(message: TestSequentialDomainEvent) {
        delay(delayMs.milliseconds)
        results.add(label)
    }
}

class DelayingSequentialAfterTransactionHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DispatchAfterTransaction<TestSequentialDomainEvent>() {
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
    DispatchImmediatelyInTransaction<TestDomainEvent>() {
    override suspend fun handle(message: TestDomainEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("Handler failed for: ${message.data}")
    }
}

// --- FailFast handlers (per dispatch phase) ---

class ThrowingFailFastHandler(private val results: MutableList<String>) :
    DispatchImmediatelyInTransaction<TestFailFastEvent>() {
    override suspend fun handle(message: TestFailFastEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("FailFast handler failed for: ${message.data}")
    }
}

class SucceedingFailFastHandler(private val results: MutableList<String>) :
    DispatchImmediatelyInTransaction<TestFailFastEvent>() {
    override suspend fun handle(message: TestFailFastEvent) {
        results.add("success:${message.data}")
    }
}

class ThrowingFailFastAtEndOfTransactionHandler(private val results: MutableList<String>) :
    DispatchAtEndOfTransaction<TestFailFastEvent>() {
    override suspend fun handle(message: TestFailFastEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("FailFast handler failed for: ${message.data}")
    }
}

class SucceedingFailFastAtEndOfTransactionHandler(private val results: MutableList<String>) :
    DispatchAtEndOfTransaction<TestFailFastEvent>() {
    override suspend fun handle(message: TestFailFastEvent) {
        results.add("success:${message.data}")
    }
}

class ThrowingFailFastAfterTransactionHandler(private val results: MutableList<String>) :
    DispatchAfterTransaction<TestFailFastEvent>() {
    override suspend fun handle(message: TestFailFastEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("FailFast handler failed for: ${message.data}")
    }
}

class SucceedingFailFastAfterTransactionHandler(private val results: MutableList<String>) :
    DispatchAfterTransaction<TestFailFastEvent>() {
    override suspend fun handle(message: TestFailFastEvent) {
        results.add("success:${message.data}")
    }
}

// --- FireAndForget handlers (per dispatch phase) ---

class ThrowingFireAndForgetHandler(private val results: MutableList<String>) :
    DispatchImmediatelyInTransaction<TestFireAndForgetEvent>() {
    override suspend fun handle(message: TestFireAndForgetEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("FireAndForget handler failed for: ${message.data}")
    }
}

class SucceedingFireAndForgetHandler(private val results: MutableList<String>) :
    DispatchImmediatelyInTransaction<TestFireAndForgetEvent>() {
    override suspend fun handle(message: TestFireAndForgetEvent) {
        results.add("success:${message.data}")
    }
}

class ThrowingFireAndForgetAtEndOfTransactionHandler(private val results: MutableList<String>) :
    DispatchAtEndOfTransaction<TestFireAndForgetEvent>() {
    override suspend fun handle(message: TestFireAndForgetEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("FireAndForget handler failed for: ${message.data}")
    }
}

class SucceedingFireAndForgetAtEndOfTransactionHandler(private val results: MutableList<String>) :
    DispatchAtEndOfTransaction<TestFireAndForgetEvent>() {
    override suspend fun handle(message: TestFireAndForgetEvent) {
        results.add("success:${message.data}")
    }
}

class ThrowingFireAndForgetAfterTransactionHandler(private val results: MutableList<String>) :
    DispatchAfterTransaction<TestFireAndForgetEvent>() {
    override suspend fun handle(message: TestFireAndForgetEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("FireAndForget handler failed for: ${message.data}")
    }
}

class SucceedingFireAndForgetAfterTransactionHandler(private val results: MutableList<String>) :
    DispatchAfterTransaction<TestFireAndForgetEvent>() {
    override suspend fun handle(message: TestFireAndForgetEvent) {
        results.add("success:${message.data}")
    }
}

// --- ContinueAndAggregate handlers (per dispatch phase) ---

class ThrowingContinueAndAggregateHandler(
    private val results: MutableList<String>,
    private val label: String,
) : DispatchImmediatelyInTransaction<TestContinueAndAggregateEvent>() {
    override suspend fun handle(message: TestContinueAndAggregateEvent) {
        results.add("threw:$label")
        throw TestHandlerException("ContinueAndAggregate handler '$label' failed")
    }
}

class SucceedingContinueAndAggregateHandler(
    private val results: MutableList<String>,
    private val label: String,
) : DispatchImmediatelyInTransaction<TestContinueAndAggregateEvent>() {
    override suspend fun handle(message: TestContinueAndAggregateEvent) {
        results.add("success:$label")
    }
}

class ThrowingContinueAndAggregateAtEndOfTransactionHandler(
    private val results: MutableList<String>,
    private val label: String,
) : DispatchAtEndOfTransaction<TestContinueAndAggregateEvent>() {
    override suspend fun handle(message: TestContinueAndAggregateEvent) {
        results.add("threw:$label")
        throw TestHandlerException("ContinueAndAggregate handler '$label' failed")
    }
}

class SucceedingContinueAndAggregateAtEndOfTransactionHandler(
    private val results: MutableList<String>,
    private val label: String,
) : DispatchAtEndOfTransaction<TestContinueAndAggregateEvent>() {
    override suspend fun handle(message: TestContinueAndAggregateEvent) {
        results.add("success:$label")
    }
}

class ThrowingContinueAndAggregateAfterTransactionHandler(
    private val results: MutableList<String>,
    private val label: String,
) : DispatchAfterTransaction<TestContinueAndAggregateEvent>() {
    override suspend fun handle(message: TestContinueAndAggregateEvent) {
        results.add("threw:$label")
        throw TestHandlerException("ContinueAndAggregate handler '$label' failed")
    }
}

class SucceedingContinueAndAggregateAfterTransactionHandler(
    private val results: MutableList<String>,
    private val label: String,
) : DispatchAfterTransaction<TestContinueAndAggregateEvent>() {
    override suspend fun handle(message: TestContinueAndAggregateEvent) {
        results.add("success:$label")
    }
}

// --- Sequential FailFast handlers (for concurrency × error strategy orthogonality) ---

class ThrowingSequentialFailFastHandler(private val results: MutableList<String>) :
    DispatchImmediatelyInTransaction<TestSequentialFailFastEvent>() {
    override suspend fun handle(message: TestSequentialFailFastEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("FailFast handler failed for: ${message.data}")
    }
}

class SucceedingSequentialFailFastHandler(private val results: MutableList<String>) :
    DispatchImmediatelyInTransaction<TestSequentialFailFastEvent>() {
    override suspend fun handle(message: TestSequentialFailFastEvent) {
        results.add("success:${message.data}")
    }
}

// --- Sequential ContinueAndAggregate handlers ---

class ThrowingSequentialContinueAndAggregateHandler(
    private val results: MutableList<String>,
    private val label: String,
) : DispatchImmediatelyInTransaction<TestSequentialContinueAndAggregateEvent>() {
    override suspend fun handle(message: TestSequentialContinueAndAggregateEvent) {
        results.add("threw:$label")
        throw TestHandlerException("ContinueAndAggregate handler '$label' failed")
    }
}

class SucceedingSequentialContinueAndAggregateHandler(
    private val results: MutableList<String>,
    private val label: String,
) : DispatchImmediatelyInTransaction<TestSequentialContinueAndAggregateEvent>() {
    override suspend fun handle(message: TestSequentialContinueAndAggregateEvent) {
        results.add("success:$label")
    }
}

// --- Sequential FireAndForget handlers ---

class ThrowingSequentialFireAndForgetHandler(private val results: MutableList<String>) :
    DispatchImmediatelyInTransaction<TestSequentialFireAndForgetEvent>() {
    override suspend fun handle(message: TestSequentialFireAndForgetEvent) {
        results.add("threw:${message.data}")
        throw TestHandlerException("FireAndForget handler failed for: ${message.data}")
    }
}

class SucceedingSequentialFireAndForgetHandler(private val results: MutableList<String>) :
    DispatchImmediatelyInTransaction<TestSequentialFireAndForgetEvent>() {
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

class TestHandlerException(message: String) : Exception(message)
