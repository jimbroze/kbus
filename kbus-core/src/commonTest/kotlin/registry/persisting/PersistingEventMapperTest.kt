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

class PersistingEventMapperTest {

    @Test
    fun test_it_does_not_allow_multiple_of_the_same_domain_event_handler() {
        val eventMapper = PersistingEventMapper()

        assertFailsWith<DuplicateEventHandlerException> {
            eventMapper.addDomainHandlers(
                TestDomainEvent::class,
                listOf(TestDomainEventHandler::class, TestDomainEventHandler::class),
            )
        }
    }

    @Test
    fun test_it_does_not_allow_duplicate_domain_handlers_across_calls() {
        val eventMapper = PersistingEventMapper()

        eventMapper.addDomainHandlers(TestDomainEvent::class, listOf(TestDomainEventHandler::class))

        assertFailsWith<DuplicateEventHandlerException> {
            eventMapper.addDomainHandlers(
                TestDomainEvent::class,
                listOf(TestDomainEventHandler::class),
            )
        }
    }

    @Test
    fun test_it_does_not_allow_multiple_of_the_same_integration_event_handler() {
        val eventMapper = PersistingEventMapper()

        assertFailsWith<DuplicateEventHandlerException> {
            eventMapper.addEventHandlers(
                StorageEvent::class,
                listOf(PrintEventHandler::class, PrintEventHandler::class),
            )
        }
    }

    @Test
    fun test_it_does_not_allow_duplicate_integration_handlers_across_calls() {
        val eventMapper = PersistingEventMapper()

        eventMapper.addEventHandlers(StorageEvent::class, listOf(PrintEventHandler::class))

        assertFailsWith<DuplicateEventHandlerException> {
            eventMapper.addEventHandlers(StorageEvent::class, listOf(PrintEventHandler::class))
        }
    }

    @Test
    fun test_handlerClassesFor_returns_empty_list_when_no_handlers_registered() {
        val mapper = PersistingEventMapper()
        val event = StorageEvent("test", mutableListOf())

        val handlerClasses = mapper.handlerClassesFor(event)

        assertEquals(0, handlerClasses.size)
    }

    @Test
    fun test_handlerClassesFor_returns_domain_event_handler_classes() {
        val mapper = PersistingEventMapper()

        mapper.addDomainHandlers(TestDomainEvent::class, listOf(TestDomainEventHandler::class))

        val event = TestDomainEvent("test data")
        val handlerClasses = mapper.handlerClassesFor(event)

        assertEquals(1, handlerClasses.size)
        assertEquals<KClass<*>>(TestDomainEventHandler::class, handlerClasses[0])
    }

    @Test
    fun test_handlerClassesFor_returns_integration_event_handler_classes() {
        val mapper = PersistingEventMapper()

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
    fun test_allows_different_handlers_for_same_event() {
        val mapper = PersistingEventMapper()

        mapper.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class, OtherPrintEventHandler::class),
        )

        val handlerClasses = mapper.handlerClassesFor(StorageEvent("test", mutableListOf()))
        assertEquals(2, handlerClasses.size)
    }

    @Test
    fun test_allows_same_handler_type_for_different_events() {
        val mapper = PersistingEventMapper()

        mapper.addEventHandlers(StorageEvent::class, listOf(PrintEventHandler::class))

        val handlerClasses = mapper.handlerClassesFor(StorageEvent("test", mutableListOf()))
        assertEquals(1, handlerClasses.size)
    }

    @Test
    fun subscribedEventTypes_containsOnlyEventClassesWithRegisteredHandlers() {
        val mapper = PersistingEventMapper()

        mapper.addEventHandlers(StorageEvent::class, listOf(PrintEventHandler::class))

        assertTrue(mapper.subscribedEventTypes().contains(StorageEvent::class))
        assertFalse(mapper.subscribedEventTypes().contains(OtherStorageEvent::class))
    }

    @Test
    fun subscribedEventTypes_separatesDomainAndIntegrationEventClasses() {
        val mapper = PersistingEventMapper()

        mapper.addDomainHandlers(TestDomainEvent::class, listOf(TestDomainEventHandler::class))

        assertTrue(mapper.subscribedEventTypes().contains(TestDomainEvent::class))
        assertFalse(mapper.subscribedEventTypes().contains(StorageEvent::class))
    }
}
