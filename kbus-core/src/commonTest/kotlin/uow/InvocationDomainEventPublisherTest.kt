package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.core.fixtures.TestDomainEventDispatcher
import com.jimbroze.kbus.core.fixtures.testInvocation
import com.jimbroze.kbus.domain.event.DomainEvent
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlinx.coroutines.test.runTest

class InvocationDomainEventPublisherTest {
    @Test
    fun `publishes to the dispatcher alongside the invocation it belongs to`() = runTest {
        val dispatcher = TestDomainEventDispatcher()
        val invocation = testInvocation<Any?>()
        val event = object : DomainEvent() {}

        InvocationDomainEventPublisher(dispatcher, invocation).publish(event)

        assertContentEquals(listOf(Pair(event, invocation)), dispatcher.dispatchedEvents)
    }
}
