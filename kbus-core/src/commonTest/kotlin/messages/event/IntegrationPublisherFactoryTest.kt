package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.core.fixtures.RecordingIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import com.jimbroze.kbus.core.fixtures.TestUnitOfWork
import com.jimbroze.kbus.core.fixtures.testInvocation
import com.jimbroze.kbus.core.uow.TransactionOutbox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class IntegrationPublisherFactoryTest {
    @Test
    fun publisherFor_resolves_to_the_invocations_publisher_when_present() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val store = RecordingOutboxStore()
        val unitOfWork = TestUnitOfWork<Any?>()
        val outbox =
            TransactionOutbox(store, RecordingIntegrationEventPublisher(), this, unitOfWork)
        val invocation = testInvocation(unitOfWork = unitOfWork, publisher = outbox)
        val factory = IntegrationPublisherFactory(basePublisher)

        factory.publisherFor(invocation).publish(listOf(TestIntegrationEvent("via-outbox")))
        unitOfWork.secondaryWork.forEach { it.invoke() }

        assertEquals(1, store.saved.size)
        assertEquals("via-outbox", (store.saved.single().event as TestIntegrationEvent).name)
        assertEquals(0, basePublisher.publishedEvents.size)
    }

    @Test
    fun publisherFor_resolves_to_the_base_publisher_for_an_invocation_using_it() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val invocation = testInvocation<Any?>(publisher = basePublisher)
        val factory = IntegrationPublisherFactory(basePublisher)

        factory.publisherFor(invocation).publish(listOf(TestIntegrationEvent("via-base")))

        assertEquals(1, basePublisher.publishedEvents.flatten().size)
        assertEquals(
            "via-base",
            (basePublisher.publishedEvents.flatten().single() as TestIntegrationEvent).name,
        )
    }

    @Test
    fun publisherFor_resolves_to_the_base_publisher_for_a_null_invocation() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val factory = IntegrationPublisherFactory(basePublisher)

        factory.publisherFor(null).publish(listOf(TestIntegrationEvent("via-base")))

        assertEquals(1, basePublisher.publishedEvents.flatten().size)
    }
}
