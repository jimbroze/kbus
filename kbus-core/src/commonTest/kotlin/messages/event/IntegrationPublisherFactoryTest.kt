package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.core.fixtures.RecordingIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import com.jimbroze.kbus.core.fixtures.TestUnitOfWork
import com.jimbroze.kbus.core.uow.TransactionOutbox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class IntegrationPublisherFactoryTest {
    @Test
    fun publisherFor_returns_the_outbox_for_a_unit_of_work_holding_one() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val outbox = TransactionOutbox(RecordingOutboxStore(), basePublisher, this, false)
        val unitOfWork = TestUnitOfWork<Any?>().apply { transactionOutbox = outbox }
        val factory = IntegrationPublisherFactory(basePublisher)

        assertEquals(outbox, factory.publisherFor(unitOfWork))
    }

    @Test
    fun publisherFor_returns_the_base_publisher_for_a_unit_of_work_with_a_null_outbox() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val unitOfWork = TestUnitOfWork<Any?>()
        val factory = IntegrationPublisherFactory(basePublisher)

        assertEquals(basePublisher, factory.publisherFor(unitOfWork))
    }

    @Test
    fun publisherFor_returns_the_base_publisher_for_a_null_unit_of_work() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val factory = IntegrationPublisherFactory(basePublisher)

        assertEquals(basePublisher, factory.publisherFor(null))
    }

    @Test
    fun busAccessFor_dispatches_through_the_outbox_for_a_unit_of_work_holding_one() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val store = RecordingOutboxStore()
        val outbox = TransactionOutbox(store, RecordingIntegrationEventPublisher(), this, false)
        val unitOfWork = TestUnitOfWork<Any?>().apply { transactionOutbox = outbox }
        val factory = IntegrationPublisherFactory(basePublisher)

        factory.busAccessFor(unitOfWork).dispatch(TestIntegrationEvent("via-outbox"))

        assertEquals(1, store.saved.size)
        assertEquals("via-outbox", (store.saved.single().event as TestIntegrationEvent).name)
        assertEquals(0, basePublisher.publishedEvents.size)
    }

    @Test
    fun busAccessFor_dispatches_through_the_base_publisher_without_an_outbox() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val unitOfWork = TestUnitOfWork<Any?>()
        val factory = IntegrationPublisherFactory(basePublisher)

        factory.busAccessFor(unitOfWork).dispatch(TestIntegrationEvent("via-base"))

        assertEquals(1, basePublisher.publishedEvents.flatten().size)
        assertEquals(
            "via-base",
            (basePublisher.publishedEvents.flatten().single() as TestIntegrationEvent).name,
        )
    }

    @Test
    fun busAccessFor_dispatches_through_the_base_publisher_for_a_null_unit_of_work() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val factory = IntegrationPublisherFactory(basePublisher)

        factory.busAccessFor(null).dispatch(TestIntegrationEvent("via-base"))

        assertEquals(1, basePublisher.publishedEvents.flatten().size)
    }

    @Test
    fun busAccessFor_passes_the_single_event_through_as_a_one_element_list() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val factory = IntegrationPublisherFactory(basePublisher)

        factory.busAccessFor(null).dispatch(TestIntegrationEvent("solo"))

        assertEquals(
            listOf(listOf("solo")),
            basePublisher.publishedEvents.map {
                it.map { event -> (event as TestIntegrationEvent).name }
            },
        )
    }
}
