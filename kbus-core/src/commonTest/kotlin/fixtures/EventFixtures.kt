package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventHandler
import com.jimbroze.kbus.core.messages.event.ContinueAndAggregateDomainEvent
import com.jimbroze.kbus.core.messages.event.DispatchAfterTransaction
import com.jimbroze.kbus.core.messages.event.DispatchAsynchronously
import com.jimbroze.kbus.core.messages.event.DispatchAtEndOfTransaction
import com.jimbroze.kbus.core.messages.event.DispatchImmediatelyInTransaction
import com.jimbroze.kbus.core.messages.event.DispatchSynchronously
import com.jimbroze.kbus.core.messages.event.DomainEventHandler
import com.jimbroze.kbus.core.messages.event.FailFastDomainEvent
import com.jimbroze.kbus.core.messages.event.FireAndForgetDomainEvent
import com.jimbroze.kbus.domain.DomainEvent
import com.jimbroze.kbus.domain.DomainEventPublisher
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

class TestDomainEvent(val data: String) : DomainEvent()

class TestFailFastEvent(val data: String) : FailFastDomainEvent()

class TestFireAndForgetEvent(val data: String) : FireAndForgetDomainEvent()

class TestContinueAndAggregateEvent(val data: String) : ContinueAndAggregateDomainEvent()

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

class DelayingDispatchSynchronouslyHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DispatchSynchronously<TestDomainEvent>() {
    override suspend fun handle(message: TestDomainEvent) {
        delay(delayMs.milliseconds)
        results.add(label)
    }
}

class DelayingDispatchAsynchronouslyHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DispatchAsynchronously<TestDomainEvent>() {
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
