package com.jimbroze.kbus.core.registry.persisting

import com.jimbroze.kbus.core.fixtures.OtherPrintEventHandler
import com.jimbroze.kbus.core.fixtures.OtherStorageEvent
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEventHandler
import com.jimbroze.kbus.core.registry.DuplicateEventHandlerException
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistingEventHandlerStoreTest {

    @Test
    fun `refuses the same domain handler class registered twice for an event`() {
        val eventHandlerStore = PersistingEventHandlerStore()

        assertFailsWith<DuplicateEventHandlerException> {
            eventHandlerStore.addDomainHandlers(
                TestDomainEvent::class,
                listOf(TestDomainEventHandler::class, TestDomainEventHandler::class),
            )
        }
    }

    @Test
    fun `refuses a domain handler class already registered in an earlier call`() {
        val eventHandlerStore = PersistingEventHandlerStore()

        eventHandlerStore.addDomainHandlers(
            TestDomainEvent::class,
            listOf(TestDomainEventHandler::class),
        )

        assertFailsWith<DuplicateEventHandlerException> {
            eventHandlerStore.addDomainHandlers(
                TestDomainEvent::class,
                listOf(TestDomainEventHandler::class),
            )
        }
    }

    @Test
    fun `refuses the same integration handler class registered twice for an event`() {
        val eventHandlerStore = PersistingEventHandlerStore()

        assertFailsWith<DuplicateEventHandlerException> {
            eventHandlerStore.addEventHandlers(
                StorageEvent::class,
                listOf(PrintEventHandler::class, PrintEventHandler::class),
            )
        }
    }

    @Test
    fun `refuses an integration handler class already registered in an earlier call`() {
        val eventHandlerStore = PersistingEventHandlerStore()

        eventHandlerStore.addEventHandlers(StorageEvent::class, listOf(PrintEventHandler::class))

        assertFailsWith<DuplicateEventHandlerException> {
            eventHandlerStore.addEventHandlers(
                StorageEvent::class,
                listOf(PrintEventHandler::class),
            )
        }
    }

    @Test
    fun `finds no handler classes for an event with none registered`() {
        val mapper = PersistingEventHandlerStore()
        val event = StorageEvent("test", mutableListOf())

        val handlerClasses = mapper.handlerClassesFor(event)

        assertEquals(0, handlerClasses.size)
    }

    @Test
    fun `finds the domain handler classes registered for an event`() {
        val mapper = PersistingEventHandlerStore()

        mapper.addDomainHandlers(TestDomainEvent::class, listOf(TestDomainEventHandler::class))

        val event = TestDomainEvent("test data")
        val handlerClasses = mapper.handlerClassesFor(event)

        assertEquals(1, handlerClasses.size)
        assertEquals<KClass<*>>(TestDomainEventHandler::class, handlerClasses[0])
    }

    @Test
    fun `finds the integration handler classes registered for an event`() {
        val mapper = PersistingEventHandlerStore()

        mapper.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class, OtherPrintEventHandler::class),
        )

        val event = StorageEvent("test", mutableListOf())
        val handlerClasses = mapper.handlerClassesFor(event)

        assertEquals(2, handlerClasses.size)
        assertEquals<KClass<*>>(PrintEventHandler::class, handlerClasses[0])
        assertEquals<KClass<*>>(OtherPrintEventHandler::class, handlerClasses[1])
    }

    @Test
    fun `accepts different handler classes for the same event`() {
        val mapper = PersistingEventHandlerStore()

        mapper.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class, OtherPrintEventHandler::class),
        )

        val handlerClasses = mapper.handlerClassesFor(StorageEvent("test", mutableListOf()))
        assertEquals(2, handlerClasses.size)
    }

    @Test
    fun `accepts one handler class registered for different events`() {
        val mapper = PersistingEventHandlerStore()

        mapper.addEventHandlers(StorageEvent::class, listOf(PrintEventHandler::class))

        val handlerClasses = mapper.handlerClassesFor(StorageEvent("test", mutableListOf()))
        assertEquals(1, handlerClasses.size)
    }

    @Test
    fun `reports only the events that have a handler registered`() {
        val mapper = PersistingEventHandlerStore()

        mapper.addEventHandlers(StorageEvent::class, listOf(PrintEventHandler::class))

        assertTrue(mapper.subscribedEventTypes().contains(StorageEvent::class))
        assertFalse(mapper.subscribedEventTypes().contains(OtherStorageEvent::class))
    }

    @Test
    fun `reports domain and integration events separately`() {
        val mapper = PersistingEventHandlerStore()

        mapper.addDomainHandlers(TestDomainEvent::class, listOf(TestDomainEventHandler::class))

        assertTrue(mapper.subscribedEventTypes().contains(TestDomainEvent::class))
        assertFalse(mapper.subscribedEventTypes().contains(StorageEvent::class))
    }
}
