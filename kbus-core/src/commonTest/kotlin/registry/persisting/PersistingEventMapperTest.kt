package com.jimbroze.kbus.core.registry.persisting

import com.jimbroze.kbus.core.fixtures.OtherPrintEventHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEventHandler
import com.jimbroze.kbus.core.registry.DuplicateEventHandlerException
import com.jimbroze.kbus.core.registry.EventHandlerMapping
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.MessageHandlerFactoryStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PersistingEventMapperTest {

    private fun createMapper(): PersistingEventMapper {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()
        store.registerHandlers(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                EventHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("test")
                },
            ),
        )
        store.registerHandlers(
            TestDomainEvent::class,
            listOf(
                EventHandlerFactory(TestDomainEventHandler::class) {
                    TestDomainEventHandler(mutableListOf())
                }
            ),
        )
        return PersistingEventMapper(PersistingEventFactory(store))
    }

    @Test
    fun test_it_does_not_allow_multiple_of_the_same_domain_event_handler() {
        val eventMapper =
            PersistingEventMapper(PersistingEventFactory(MessageHandlerFactoryStore()))

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
        val eventMapper =
            PersistingEventMapper(PersistingEventFactory(MessageHandlerFactoryStore()))

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
    fun test_handlersFor_returns_empty_list_when_no_handlers_registered() {
        val mapper = createMapper()
        val event = StorageEvent("test", mutableListOf())

        val handlers = mapper.handlersFor(event)

        assertEquals(0, handlers.size)
    }

    @Test
    fun test_handlersFor_returns_domain_event_handlers() {
        val mapper = createMapper()

        mapper.addDomainHandlers(
            listOf(
                EventHandlerMapping(TestDomainEvent::class, listOf(TestDomainEventHandler::class))
            )
        )

        val event = TestDomainEvent("test data")
        val handlers = mapper.handlersFor(event)

        assertEquals(1, handlers.size)
        assertIs<TestDomainEventHandler>(handlers[0])
    }

    @Test
    fun test_handlersFor_returns_integration_event_handlers() {
        val mapper = createMapper()

        mapper.addEventHandlers(
            listOf(
                EventHandlerMapping(
                    StorageEvent::class,
                    listOf(PrintEventHandler::class, OtherPrintEventHandler::class),
                )
            )
        )

        val event = StorageEvent("test", mutableListOf())
        val handlers = mapper.handlersFor(event)

        assertEquals(2, handlers.size)
        assertIs<PrintEventHandler>(handlers[0])
        assertIs<OtherPrintEventHandler>(handlers[1])
    }

    @Test
    fun test_allows_different_handlers_for_same_event() {
        val mapper = createMapper()

        mapper.addEventHandlers(
            listOf(
                EventHandlerMapping(
                    StorageEvent::class,
                    listOf(PrintEventHandler::class, OtherPrintEventHandler::class),
                )
            )
        )

        val handlers = mapper.handlersFor(StorageEvent("test", mutableListOf()))
        assertEquals(2, handlers.size)
    }

    @Test
    fun test_allows_same_handler_type_for_different_events() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()
        store.registerHandlers(
            StorageEvent::class,
            listOf(EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() }),
        )
        val mapper = PersistingEventMapper(PersistingEventFactory(store))

        mapper.addEventHandlers(
            listOf(EventHandlerMapping(StorageEvent::class, listOf(PrintEventHandler::class)))
        )

        val handlers = mapper.handlersFor(StorageEvent("test", mutableListOf()))
        assertEquals(1, handlers.size)
    }
}
