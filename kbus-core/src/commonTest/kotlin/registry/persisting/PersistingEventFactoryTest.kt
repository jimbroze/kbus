package com.jimbroze.kbus.core.registry.persisting

import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.core.fixtures.OtherPrintEventHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.MessageHandlerFactoryStore
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PersistingEventFactoryTest {
    @Suppress("UNCHECKED_CAST")
    private fun handlerClasses(
        vararg classes: KClass<out EventHandler<StorageEvent>>
    ): List<KClass<EventHandler<StorageEvent>>> =
        classes.toList() as List<KClass<EventHandler<StorageEvent>>>

    private fun createStoreAndFactory():
        Pair<MessageHandlerFactoryStore<Event>, PersistingEventFactory> {
        val store = MessageHandlerFactoryStore<Event>()
        store.registerHandlers(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                EventHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("test")
                },
            ),
        )
        return store to PersistingEventFactory(store)
    }

    @Test
    fun test_create_returns_handlers_in_order_of_requested_classes() {
        val (_, factory) = createStoreAndFactory()

        val handlers =
            factory.create(
                StorageEvent::class,
                handlerClasses(PrintEventHandler::class, OtherPrintEventHandler::class),
            )

        assertEquals(2, handlers.size)
        assertIs<PrintEventHandler>(handlers[0])
        assertIs<OtherPrintEventHandler>(handlers[1])
    }

    @Test
    fun test_create_returns_handlers_in_reversed_order() {
        val (_, factory) = createStoreAndFactory()

        val handlers =
            factory.create(
                StorageEvent::class,
                handlerClasses(OtherPrintEventHandler::class, PrintEventHandler::class),
            )

        assertEquals(2, handlers.size)
        assertIs<OtherPrintEventHandler>(handlers[0])
        assertIs<PrintEventHandler>(handlers[1])
    }

    @Test
    fun test_create_returns_single_handler() {
        val (_, factory) = createStoreAndFactory()

        val handlers = factory.create(StorageEvent::class, handlerClasses(PrintEventHandler::class))

        assertEquals(1, handlers.size)
        assertIs<PrintEventHandler>(handlers[0])
    }

    @Test
    fun test_create_throws_when_handler_factory_not_found() {
        val store = MessageHandlerFactoryStore<Event>()
        val factory = PersistingEventFactory(store)

        assertFailsWith<IllegalStateException> {
            factory.create(StorageEvent::class, handlerClasses(PrintEventHandler::class))
        }
    }

    @Test
    fun test_it_creates_new_instances_of_handlers_on_each_request() {
        val (_, factory) = createStoreAndFactory()

        val handlers1 =
            factory.create(StorageEvent::class, handlerClasses(PrintEventHandler::class))
        val handlers2 =
            factory.create(StorageEvent::class, handlerClasses(PrintEventHandler::class))

        assertIs<PrintEventHandler>(handlers1[0])
        assertIs<PrintEventHandler>(handlers2[0])
        assertTrue("Expected different instances") { handlers1[0] !== handlers2[0] }
    }
}
