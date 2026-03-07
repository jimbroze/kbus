package com.jimbroze.kbus.core.registry.persisting

import com.jimbroze.kbus.core.messages.event.TestDomainEvent
import com.jimbroze.kbus.core.messages.event.TestDomainEventHandler
import com.jimbroze.kbus.core.registry.DuplicateEventHandlerException
import com.jimbroze.kbus.core.registry.EventAndHandlerFactories
import com.jimbroze.kbus.core.registry.EventHandlerMapping
import com.jimbroze.kbus.core.registry.OtherPrintEventHandler
import com.jimbroze.kbus.core.registry.PrintEventHandler
import com.jimbroze.kbus.core.registry.StorageEvent
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.MessageHandlerFactoryStore
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PersistingEventMapperTest {
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
    fun test_it_does_not_allow_multiple_of_the_same_integration_event_handler_when_adding_inline() {
        val eventMapper =
            PersistingEventMapper(PersistingEventFactory(MessageHandlerFactoryStore()))

        assertFailsWith<DuplicateEventHandlerException> {
            eventMapper.addInlineEventHandlers(
                listOf(
                    EventAndHandlerFactories(
                        StorageEvent::class,
                        listOf(
                            EventHandlerFactory(OtherPrintEventHandler::class) {
                                OtherPrintEventHandler("Still testing the bus")
                            }
                        ),
                    ),
                    EventAndHandlerFactories(
                        StorageEvent::class,
                        listOf(
                            EventHandlerFactory(OtherPrintEventHandler::class) {
                                OtherPrintEventHandler("Still testing the bus")
                            }
                        ),
                    ),
                )
            )
        }

        assertFailsWith<DuplicateEventHandlerException> {
            eventMapper.addInlineEventHandlers(
                listOf(
                    EventAndHandlerFactories(
                        StorageEvent::class,
                        listOf(
                            EventHandlerFactory(OtherPrintEventHandler::class) {
                                OtherPrintEventHandler("Still testing the bus")
                            },
                            EventHandlerFactory(OtherPrintEventHandler::class) {
                                OtherPrintEventHandler("Still testing the bus")
                            },
                        ),
                    )
                )
            )
        }
    }
}
