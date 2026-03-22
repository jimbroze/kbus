package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventHandler
import com.jimbroze.kbus.core.messages.event.DispatchAfterTransaction
import com.jimbroze.kbus.core.messages.event.DispatchAsynchronously
import com.jimbroze.kbus.core.messages.event.DispatchAtEndOfTransaction
import com.jimbroze.kbus.core.messages.event.DispatchImmediatelyInTransaction
import com.jimbroze.kbus.core.messages.event.DispatchSynchronously
import com.jimbroze.kbus.core.messages.event.DomainEventHandler
import com.jimbroze.kbus.domain.DomainEvent
import com.jimbroze.kbus.domain.DomainEventPublisher
import kotlinx.coroutines.delay

class TestDomainEvent(val data: String) : DomainEvent()

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
        delay(delayMs)
        results.add(label)
    }
}

class DelayingDispatchImmediatelyHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DispatchImmediatelyInTransaction<TestDomainEvent>() {
    override suspend fun handle(message: TestDomainEvent) {
        delay(delayMs)
        results.add(label)
    }
}

class DelayingDispatchAtEndOfTransactionHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DispatchAtEndOfTransaction<TestDomainEvent>() {
    override suspend fun handle(message: TestDomainEvent) {
        delay(delayMs)
        results.add(label)
    }
}

class DelayingDispatchSynchronouslyHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DispatchSynchronously<TestDomainEvent>() {
    override suspend fun handle(message: TestDomainEvent) {
        delay(delayMs)
        results.add(label)
    }
}

class DelayingDispatchAsynchronouslyHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DispatchAsynchronously<TestDomainEvent>() {
    override suspend fun handle(message: TestDomainEvent) {
        delay(delayMs)
        results.add(label)
    }
}

class DelayingDispatchAfterTransactionHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : DispatchAfterTransaction<TestDomainEvent>() {
    override suspend fun handle(message: TestDomainEvent) {
        delay(delayMs)
        results.add(label)
    }
}

class TestIntegrationEvent(val name: String) : IntegrationEvent()

class SimpleIntegrationEventHandler(private val results: MutableList<String>) :
    IntegrationEventHandler<TestIntegrationEvent> {
    override suspend fun handle(message: TestIntegrationEvent) {
        results.add(message.name)
    }
}

class DelayingIntegrationEventHandler(
    private val results: MutableList<String>,
    private val delayMs: Long,
    private val label: String,
) : IntegrationEventHandler<TestIntegrationEvent> {
    override suspend fun handle(message: TestIntegrationEvent) {
        delay(delayMs)
        results.add(label)
    }
}
