package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.messages.event.DispatchAfterTransaction
import com.jimbroze.kbus.core.messages.event.DispatchAtEndOfTransaction
import com.jimbroze.kbus.core.messages.event.DomainEventHandler
import com.jimbroze.kbus.domain.DomainEvent
import com.jimbroze.kbus.domain.DomainEventPublisher

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

class TestIntegrationEvent(val name: String) : IntegrationEvent()
