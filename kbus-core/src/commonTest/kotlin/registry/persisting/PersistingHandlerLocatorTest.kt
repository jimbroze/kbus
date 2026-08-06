package com.jimbroze.kbus.core.registry.persisting

import com.jimbroze.kbus.core.fixtures.OtherPrintEventHandler
import com.jimbroze.kbus.core.fixtures.OtherStorageEvent
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageCommand
import com.jimbroze.kbus.core.fixtures.StorageCommandHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.StorageQuery
import com.jimbroze.kbus.core.fixtures.StorageQueryHandler
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEventHandler
import com.jimbroze.kbus.core.fixtures.noPublishHandlerDependencies
import com.jimbroze.kbus.core.fixtures.testCommandDependencies
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.persisting.store.QueryHandlerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PersistingHandlerLocatorTest {
    private fun createLocatorWithStorageEventHandlers(): PersistingHandlerLocator {
        val stores = HandlerFactoryStoreCollection()
        stores.eventStore.registerHandlers(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                EventHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("test")
                },
            ),
        )
        return PersistingHandlerLocator(stores)
    }

    @Test
    fun `finds no handler for a command with none registered`() {
        val locator = PersistingHandlerLocator()
        val command = StorageCommand("test", mutableListOf())

        val initialHandler = locator.handlerFor(command, testCommandDependencies<Any?>())
        assertEquals(null, initialHandler)
    }

    @Test
    fun `builds the handler registered for a command`() {
        val command = StorageCommand("test", mutableListOf())
        val stores = HandlerFactoryStoreCollection()

        val factory = PersistingHandlerLocator(stores)

        //  TODO test for CommandDependencies passed into handler
        stores.commandStore.registerHandlers(
            StorageCommand::class,
            listOf(CommandHandlerFactory(StorageCommandHandler::class) { StorageCommandHandler() }),
        )

        val registeredHandler = factory.handlerFor(command, testCommandDependencies<Any?>())
        assertIs<StorageCommandHandler>(registeredHandler)
    }

    @Test
    fun `finds no event handlers while the event has no mapping`() {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)

        val eventType = StorageEvent::class
        val event = StorageEvent("test", mutableListOf())

        assertEquals(0, locator.handlersFor(event, noPublishHandlerDependencies).size)

        stores.eventStore.registerHandlers(
            eventType,
            listOf(
                EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                EventHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("Still testing the bus")
                },
            ),
        )

        val handlers = locator.handlersFor(event, noPublishHandlerDependencies)
        assertEquals(0, handlers.size)
    }

    @Test
    fun `builds the handlers mapped to an event`() {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)

        val domainEvent = TestDomainEvent("test")
        val integrationEvent = StorageEvent("test", mutableListOf())

        assertEquals(0, locator.handlersFor(integrationEvent, noPublishHandlerDependencies).size)

        stores.eventStore.registerHandlers(
            TestDomainEvent::class,
            listOf(
                EventHandlerFactory(TestDomainEventHandler::class) {
                    TestDomainEventHandler(mutableListOf())
                }
            ),
        )
        stores.eventStore.registerHandlers(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                EventHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("Still testing the bus")
                },
            ),
        )

        locator.domainEventMapper.addDomainHandlers(
            TestDomainEvent::class,
            listOf(TestDomainEventHandler::class),
        )
        locator.integrationEventMapper.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class),
        )

        assertEquals(1, locator.domainHandlersFor(domainEvent, noPublishHandlerDependencies).size)
        assertEquals(1, locator.handlersFor(integrationEvent, noPublishHandlerDependencies).size)
    }

    @Test
    fun `refuses an event whose mapped handler was never registered`() {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)

        val event = StorageEvent("test", mutableListOf())

        assertEquals(0, locator.handlersFor(event, noPublishHandlerDependencies).size)

        locator.integrationEventMapper.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class, OtherPrintEventHandler::class),
        )

        assertFailsWith<IllegalStateException> {
            locator.handlersFor(event, noPublishHandlerDependencies)
        }
    }

    @Test
    fun `builds event handlers in the order their classes were mapped`() {
        val locator = createLocatorWithStorageEventHandlers()
        locator.integrationEventMapper.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class, OtherPrintEventHandler::class),
        )

        val handlers =
            locator.handlersFor(StorageEvent("test", mutableListOf()), noPublishHandlerDependencies)

        assertEquals(2, handlers.size)
        assertIs<PrintEventHandler>(handlers[0])
        assertIs<OtherPrintEventHandler>(handlers[1])
    }

    @Test
    fun `follows the mapped order even when it reverses the registration order`() {
        val locator = createLocatorWithStorageEventHandlers()
        locator.integrationEventMapper.addEventHandlers(
            StorageEvent::class,
            listOf(OtherPrintEventHandler::class, PrintEventHandler::class),
        )

        val handlers =
            locator.handlersFor(StorageEvent("test", mutableListOf()), noPublishHandlerDependencies)

        assertEquals(2, handlers.size)
        assertIs<OtherPrintEventHandler>(handlers[0])
        assertIs<PrintEventHandler>(handlers[1])
    }

    @Test
    fun `builds only the handler class an event maps to`() {
        val locator = createLocatorWithStorageEventHandlers()
        locator.integrationEventMapper.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class),
        )

        val handlers =
            locator.handlersFor(StorageEvent("test", mutableListOf()), noPublishHandlerDependencies)

        assertEquals(1, handlers.size)
        assertIs<PrintEventHandler>(handlers[0])
    }

    @Test
    fun `builds a fresh handler instance for every request`() {
        val locator = createLocatorWithStorageEventHandlers()
        locator.integrationEventMapper.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class),
        )

        val handlers1 =
            locator.handlersFor(StorageEvent("test", mutableListOf()), noPublishHandlerDependencies)
        val handlers2 =
            locator.handlersFor(StorageEvent("test", mutableListOf()), noPublishHandlerDependencies)

        assertIs<PrintEventHandler>(handlers1[0])
        assertIs<PrintEventHandler>(handlers2[0])
        assertTrue("Expected different instances") { handlers1[0] !== handlers2[0] }
    }

    @Test
    fun `reports only the events that have a handler registered`() {
        val locator = createLocatorWithStorageEventHandlers()
        locator.integrationEventMapper.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class),
        )

        assertTrue(locator.subscribedEventTypes().contains(StorageEvent::class))
        assertFalse(locator.subscribedEventTypes().contains(OtherStorageEvent::class))
    }

    @Test
    fun `reports its events without building any handler`() {
        var creations = 0
        val stores = HandlerFactoryStoreCollection()
        stores.eventStore.registerHandlers(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(PrintEventHandler::class) {
                    creations++
                    PrintEventHandler()
                }
            ),
        )
        val locator = PersistingHandlerLocator(stores)
        locator.integrationEventMapper.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class),
        )

        locator.subscribedEventTypes()

        assertEquals(0, creations)
    }

    @Test
    fun `reports only the commands that have a handler registered`() {
        val stores = HandlerFactoryStoreCollection()
        stores.commandStore.registerHandlers(
            StorageCommand::class,
            listOf(CommandHandlerFactory(StorageCommandHandler::class) { StorageCommandHandler() }),
        )
        val locator = PersistingHandlerLocator(stores)

        assertEquals(setOf(StorageCommand::class), locator.handledCommandTypes())
    }

    @Test
    fun `reports its commands without building any handler`() {
        var creations = 0
        val stores = HandlerFactoryStoreCollection()
        stores.commandStore.registerHandlers(
            StorageCommand::class,
            listOf(
                CommandHandlerFactory(StorageCommandHandler::class) {
                    creations++
                    StorageCommandHandler()
                }
            ),
        )
        val locator = PersistingHandlerLocator(stores)

        locator.handledCommandTypes()

        assertEquals(0, creations)
    }

    @Test
    fun `reports only the queries that have a handler registered`() {
        val stores = HandlerFactoryStoreCollection()
        stores.queryStore.registerHandlers(
            StorageQuery::class,
            listOf(QueryHandlerFactory(StorageQueryHandler::class) { StorageQueryHandler() }),
        )
        val locator = PersistingHandlerLocator(stores)

        assertEquals(setOf(StorageQuery::class), locator.handledQueryTypes())
    }

    @Test
    fun `reports its queries without building any handler`() {
        var creations = 0
        val stores = HandlerFactoryStoreCollection()
        stores.queryStore.registerHandlers(
            StorageQuery::class,
            listOf(
                QueryHandlerFactory(StorageQueryHandler::class) {
                    creations++
                    StorageQueryHandler()
                }
            ),
        )
        val locator = PersistingHandlerLocator(stores)

        locator.handledQueryTypes()

        assertEquals(0, creations)
    }
}
