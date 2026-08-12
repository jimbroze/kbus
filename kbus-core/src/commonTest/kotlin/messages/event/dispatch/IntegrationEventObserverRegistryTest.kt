package com.jimbroze.kbus.core.messages.event.dispatch

import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

class IntegrationEventObserverRegistryTest {

    @Test
    fun `hands out one shared flow per event type`() = runTest {
        val registry = IntegrationEventObserverRegistry()

        val flow1 = registry.observableFor(TestIntegrationEvent::class)
        val flow2 = registry.observableFor(TestIntegrationEvent::class)

        assertSame(flow1, flow2)
    }

    @Test
    fun `delivers an emitted event to a collector of its type`() = runTest {
        val registry = IntegrationEventObserverRegistry()

        val received = mutableListOf<TestIntegrationEvent>()
        val flow = registry.observableFor(TestIntegrationEvent::class)
        val job = backgroundScope.launch { flow.take(1).toList(received) }
        yield()

        registry.emit(TestIntegrationEvent("hello"))
        job.join()

        assertEquals(1, received.size)
        assertEquals("hello", received[0].name)
    }

    @Test
    fun `drops an emitted event when nothing is observing its type`() = runTest {
        val registry = IntegrationEventObserverRegistry()

        // Should not throw
        registry.emit(TestIntegrationEvent("unobserved"))
    }

    @Test
    fun `delivers an emitted event to every collector of its type`() = runTest {
        val registry = IntegrationEventObserverRegistry()

        val received1 = mutableListOf<TestIntegrationEvent>()
        val received2 = mutableListOf<TestIntegrationEvent>()

        val flow = registry.observableFor(TestIntegrationEvent::class)
        val job1 = backgroundScope.launch { flow.take(1).toList(received1) }
        val job2 = backgroundScope.launch { flow.take(1).toList(received2) }
        yield()

        registry.emit(TestIntegrationEvent("shared"))
        job1.join()
        job2.join()

        assertEquals("shared", received1.single().name)
        assertEquals("shared", received2.single().name)
    }
}
