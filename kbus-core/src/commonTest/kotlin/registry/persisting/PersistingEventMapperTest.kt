package com.jimbroze.kbus.core.registry.persisting

import com.jimbroze.kbus.core.fixtures.OtherPrintEventHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEventHandler
import com.jimbroze.kbus.core.registry.DuplicateEventHandlerException
import com.jimbroze.kbus.core.registry.EventHandlerMapping
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PersistingEventMapperTest {

    @Test
    fun test_it_does_not_allow_multiple_of_the_same_domain_event_handler() {
        val eventMapper = PersistingEventMapper()

        assertFailsWith<DuplicateEventHandlerException> {
            eventMapper.addDomainHandlers(
                listOf(
                    EventHandlerMapping(
                        TestDomainEvent::class,
                        listOf(TestDomainEventHandler::class, TestDomainEventHandler::class),
                    )
                )
            )
        }

        assertFailsWith<DuplicateEventHandlerException> {
            eventMapper.addDomainHandlers(
                listOf(
                    EventHandlerMapping(
                        TestDomainEvent::class,
                        listOf(TestDomainEventHandler::class),
                    ),
                    EventHandlerMapping(
                        TestDomainEvent::class,
                        listOf(TestDomainEventHandler::class),
                    ),
                )
            )
        }
    }

    @Test
    fun test_it_does_not_allow_multiple_of_the_same_integration_event_handler() {
        val eventMapper = PersistingEventMapper()

        assertFailsWith<DuplicateEventHandlerException> {
            eventMapper.addEventHandlers(
                listOf(
                    EventHandlerMapping(
                        StorageEvent::class,
                        listOf(PrintEventHandler::class, PrintEventHandler::class),
                    )
                )
            )
        }

        assertFailsWith<DuplicateEventHandlerException> {
            eventMapper.addEventHandlers(
                listOf(
                    EventHandlerMapping(StorageEvent::class, listOf(PrintEventHandler::class)),
                    EventHandlerMapping(StorageEvent::class, listOf(PrintEventHandler::class)),
                )
            )
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

        mapper.addDomainHandlers(
            listOf(
                EventHandlerMapping(TestDomainEvent::class, listOf(TestDomainEventHandler::class))
            )
        )

        val event = TestDomainEvent("test data")
        val handlerClasses = mapper.handlerClassesFor(event)

        assertEquals(1, handlerClasses.size)
        assertEquals<KClass<*>>(TestDomainEventHandler::class, handlerClasses[0])
    }

    @Test
    fun test_handlerClassesFor_returns_integration_event_handler_classes() {
        val mapper = PersistingEventMapper()

        mapper.addEventHandlers(
            listOf(
                EventHandlerMapping(
                    StorageEvent::class,
                    listOf(PrintEventHandler::class, OtherPrintEventHandler::class),
                )
            )
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
            listOf(
                EventHandlerMapping(
                    StorageEvent::class,
                    listOf(PrintEventHandler::class, OtherPrintEventHandler::class),
                )
            )
        )

        val handlerClasses = mapper.handlerClassesFor(StorageEvent("test", mutableListOf()))
        assertEquals(2, handlerClasses.size)
    }

    @Test
    fun test_allows_same_handler_type_for_different_events() {
        val mapper = PersistingEventMapper()

        mapper.addEventHandlers(
            listOf(EventHandlerMapping(StorageEvent::class, listOf(PrintEventHandler::class)))
        )

        val handlerClasses = mapper.handlerClassesFor(StorageEvent("test", mutableListOf()))
        assertEquals(1, handlerClasses.size)
    }
}
