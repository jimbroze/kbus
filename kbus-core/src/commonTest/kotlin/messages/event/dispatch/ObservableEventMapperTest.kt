package com.jimbroze.kbus.core.messages.event.dispatch

import com.jimbroze.kbus.api.messages.event.EventObserver
import com.jimbroze.kbus.api.messages.event.ObservableEventPublisher
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

class ObservableEventMapperTest {
    @Test
    fun `emits an event it is given`() = runTest {
        val mapper = ObservableEventMapper<TestIntegrationEvent>()

        val received = mutableListOf<TestIntegrationEvent>()
        val job = backgroundScope.launch { mapper.events.take(1).toList(received) }
        yield() // let collector subscribe before emitting

        mapper.emit(TestIntegrationEvent("hello"))
        job.join()

        assertEquals(1, received.size)
        assertEquals("hello", received[0].name)
    }

    @Test
    fun `delivers an emitted event to a collector`() = runTest {
        val mapper = ObservableEventMapper<TestIntegrationEvent>()
        val publisher: ObservableEventPublisher<TestIntegrationEvent> = mapper
        val observer: EventObserver<TestIntegrationEvent> = mapper

        val received = mutableListOf<TestIntegrationEvent>()
        val job = backgroundScope.launch { observer.events.first().also { received.add(it) } }
        yield()

        publisher.emit(TestIntegrationEvent("interface-test"))
        job.join()

        assertEquals(1, received.size)
        assertEquals("interface-test", received[0].name)
    }

    @Test
    fun `delivers several emitted events in the order they were emitted`() = runTest {
        val mapper = ObservableEventMapper<TestIntegrationEvent>()
        val publisher: ObservableEventPublisher<TestIntegrationEvent> = mapper
        val observer: EventObserver<TestIntegrationEvent> = mapper

        val received = mutableListOf<TestIntegrationEvent>()
        val job = backgroundScope.launch { observer.events.take(3).toList(received) }
        yield()

        publisher.emit(TestIntegrationEvent("first"))
        publisher.emit(TestIntegrationEvent("second"))
        publisher.emit(TestIntegrationEvent("third"))
        job.join()

        assertEquals(listOf("first", "second", "third"), received.map { it.name })
    }

    @Test
    fun `delivers an emitted event to every collector`() = runTest {
        val mapper = ObservableEventMapper<TestIntegrationEvent>()
        val publisher: ObservableEventPublisher<TestIntegrationEvent> = mapper
        val observer: EventObserver<TestIntegrationEvent> = mapper

        val received1 = mutableListOf<TestIntegrationEvent>()
        val received2 = mutableListOf<TestIntegrationEvent>()
        val job1 = backgroundScope.launch { observer.events.take(1).toList(received1) }
        val job2 = backgroundScope.launch { observer.events.take(1).toList(received2) }

        yield() // let both collectors subscribe

        publisher.emit(TestIntegrationEvent("shared"))
        job1.join()
        job2.join()

        assertEquals(1, received1.size)
        assertEquals("shared", received1[0].name)
        assertEquals(1, received2.size)
        assertEquals("shared", received2[0].name)
    }

    @Test
    fun `delivers nothing already emitted to a collector that subscribes later`() = runTest {
        val mapper = ObservableEventMapper<TestIntegrationEvent>()
        val publisher: ObservableEventPublisher<TestIntegrationEvent> = mapper
        val observer: EventObserver<TestIntegrationEvent> = mapper

        publisher.emit(TestIntegrationEvent("early")) // no subscribers yet

        val received = mutableListOf<TestIntegrationEvent>()
        val job = backgroundScope.launch { observer.events.take(1).toList(received) }
        yield() // let collector subscribe

        publisher.emit(TestIntegrationEvent("late"))
        job.join()

        assertEquals(1, received.size)
        assertEquals("late", received[0].name)
        assertFalse(received.any { it.name == "early" })
    }

    @Test
    fun `delivers every event of a rapid succession`() = runTest {
        val mapper = ObservableEventMapper<TestIntegrationEvent>()
        val eventCount = 100
        val publisher: ObservableEventPublisher<TestIntegrationEvent> = mapper
        val observer: EventObserver<TestIntegrationEvent> = mapper

        val received = mutableListOf<TestIntegrationEvent>()
        val job = backgroundScope.launch { observer.events.take(eventCount).toList(received) }
        yield()

        repeat(eventCount) { i -> publisher.emit(TestIntegrationEvent("event-$i")) }
        job.join()

        assertEquals(eventCount, received.size)
        assertEquals("event-0", received[0].name)
        assertEquals("event-${eventCount - 1}", received[eventCount - 1].name)
    }
}
