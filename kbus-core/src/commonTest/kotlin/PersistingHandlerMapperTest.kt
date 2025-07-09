package com.jimbroze.kbus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PersistingHandlerMapperTest {
    @Test
    fun test_it_returns_null_for_command_not_registered() {
        val mapper = PersistingHandlerMapper()
        val command = StorageCommand("test", mutableListOf())

        val handler = mapper.handlerFor(command, testCommandDependencies<Any?>())
        assertNull(handler)
    }

    @Test
    fun test_it_returns_null_for_query_not_registered() {
        val mapper = PersistingHandlerMapper()

        val handler = mapper.handlerFor(StorageQuery(0, mutableListOf()))

        assertNull(handler)
    }

    @Test
    fun test_it_returns_empty_list_for_event_not_registered() {
        val mapper = PersistingHandlerMapper()

        val handlers = mapper.handlersFor(StorageEvent("test", mutableListOf()))

        assertEquals(0, handlers.size)
    }

    @Test
    fun test_it_creates_a_registered_command_handler() {
        val mapper = PersistingHandlerMapper()
        val command = StorageCommand("test", mutableListOf())

        mapper.register(
            StorageCommand::class,
            CommandHandlerFactory(StorageCommandHandler::class) { StorageCommandHandler() },
        )

        val registeredHandler = mapper.handlerFor(command, testCommandDependencies<Any?>())
        assertIs<StorageCommandHandler>(registeredHandler)
    }

    @Test
    fun test_it_creates_a_registered_query_handler() {
        val mapper = PersistingHandlerMapper()
        val query = StorageQuery(0, mutableListOf())

        mapper.register(
            StorageQuery::class,
            QueryHandlerFactory(StorageQueryHandler::class) { StorageQueryHandler() },
        )

        val registeredHandler = mapper.handlerFor(query)
        assertIs<StorageQueryHandler>(registeredHandler)
    }

    @Test
    fun test_it_creates_multiple_registered_event_handlers() {
        val mapper = PersistingHandlerMapper()

        mapper.register(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                EventHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("Still testing the bus")
                },
            ),
        )

        val handlers = mapper.handlersFor(StorageEvent("test", mutableListOf()))

        assertEquals(2, handlers.size)
        assertIs<PrintEventHandler>(handlers[0])
        assertIs<OtherPrintEventHandler>(handlers[1])
    }

    @Test
    fun test_it_deregisters_a_command() {
        val mapper = PersistingHandlerMapper()
        val commandType = StorageCommand::class
        val command = StorageCommand("test", mutableListOf())

        mapper.register(
            commandType,
            CommandHandlerFactory(StorageCommandHandler::class) { StorageCommandHandler() },
        )
        assertNotNull(mapper.handlerFor(command, testCommandDependencies<Any?>()))

        mapper.deregister(commandType, StorageCommandHandler::class)

        assertNull(mapper.handlerFor(command, testCommandDependencies<Any?>()))
    }

    @Test
    fun test_it_deregisters_a_query() {
        val mapper = PersistingHandlerMapper()
        val queryType = StorageQuery::class
        val query = StorageQuery(0, mutableListOf())

        mapper.register(
            queryType,
            QueryHandlerFactory(StorageQueryHandler::class) { StorageQueryHandler() },
        )
        assertNotNull(mapper.handlerFor(query))

        mapper.deregister(queryType, StorageQueryHandler::class)

        assertNull(mapper.handlerFor(query))
    }

    @Test
    fun test_it_deregisters_events() {
        val mapper = PersistingHandlerMapper()
        val eventType = StorageEvent::class
        val event = StorageEvent("Test", mutableListOf())

        mapper.register(
            eventType,
            listOf(
                EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                EventHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("Still testing the bus")
                },
            ),
        )
        assertEquals(2, mapper.handlersFor(event).size)

        mapper.deregister(eventType, listOf(PrintEventHandler::class))

        val handlers = mapper.handlersFor(event)
        assertEquals(1, handlers.size)
        assertIs<OtherPrintEventHandler>(handlers[0])
    }
}
