package com.jimbroze.kbus.core.middleware

import com.jimbroze.kbus.core.fixtures.RecordingIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.fixtures.TestUnitOfWork
import com.jimbroze.kbus.core.messages.event.IntegrationPublisherFactory
import com.jimbroze.kbus.core.uow.TransactionOutbox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.test.runTest

class MiddlewareInvocationContextFactoryTest {
    @Test
    fun contextFor_a_unit_of_work_with_an_outbox_exposes_that_outbox() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val outbox = TransactionOutbox(RecordingOutboxStore(), basePublisher, this, false)
        val unitOfWork = TestUnitOfWork<Any?>().apply { transactionOutbox = outbox }
        val factory = MiddlewareInvocationContextFactory(IntegrationPublisherFactory(basePublisher))

        assertEquals(outbox, factory.contextFor(unitOfWork).integrationEventPublisher)
    }

    @Test
    fun contextFor_a_unit_of_work_without_an_outbox_exposes_the_base_publisher() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val unitOfWork = TestUnitOfWork<Any?>()
        val factory = MiddlewareInvocationContextFactory(IntegrationPublisherFactory(basePublisher))

        assertEquals(basePublisher, factory.contextFor(unitOfWork).integrationEventPublisher)
    }

    @Test
    fun contextFor_null_exposes_the_base_publisher() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val factory = MiddlewareInvocationContextFactory(IntegrationPublisherFactory(basePublisher))

        assertEquals(basePublisher, factory.contextFor(null).integrationEventPublisher)
    }

    @Test
    fun resolution_is_per_call_not_cached() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val outbox = TransactionOutbox(RecordingOutboxStore(), basePublisher, this, false)
        val unitOfWorkWithOutbox = TestUnitOfWork<Any?>().apply { transactionOutbox = outbox }
        val unitOfWorkWithoutOutbox = TestUnitOfWork<Any?>()
        val factory = MiddlewareInvocationContextFactory(IntegrationPublisherFactory(basePublisher))

        val firstPublisher = factory.contextFor(unitOfWorkWithOutbox).integrationEventPublisher
        val secondPublisher = factory.contextFor(unitOfWorkWithoutOutbox).integrationEventPublisher

        assertNotEquals(firstPublisher, secondPublisher)
    }
}
