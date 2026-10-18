package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.core.fixtures.BrokenStateFailureCommandHandler
import com.jimbroze.kbus.core.fixtures.FailureCommand
import com.jimbroze.kbus.core.fixtures.FailureCommandFailure
import com.jimbroze.kbus.core.fixtures.FailureQuery
import com.jimbroze.kbus.core.fixtures.FailureQueryHandler
import com.jimbroze.kbus.core.fixtures.ReturnCommand
import com.jimbroze.kbus.core.fixtures.ReturnCommandHandler
import com.jimbroze.kbus.core.fixtures.StorageCommand
import com.jimbroze.kbus.core.fixtures.StorageCommandHandler
import com.jimbroze.kbus.core.fixtures.StorageQuery
import com.jimbroze.kbus.core.fixtures.StorageQueryHandler
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.persisting.store.QueryHandlerFactory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MessageBusTest {
    @Test
    fun `executes a command through its registered handler`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        stores.commandStore.registerHandlers(
            StorageCommand::class,
            listOf(CommandHandlerFactory(StorageCommandHandler::class) { StorageCommandHandler() }),
        )

        val bus = MessageBus(PersistingHandlerLocator(stores))
        val list = mutableListOf<String>()

        val result = bus.execute(StorageCommand("Test the bus", list))

        assertTrue(result.isSuccess)
        assertContains(list, "Test the bus")
    }

    @Test
    fun `returns the value a command's handler produced`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        stores.commandStore.registerHandlers(
            ReturnCommand::class,
            listOf(CommandHandlerFactory(ReturnCommandHandler::class) { ReturnCommandHandler() }),
        )

        val bus = MessageBus(PersistingHandlerLocator(stores))

        val result = bus.execute(ReturnCommand("Test the bus"))

        assertTrue(result.isSuccess)
        assertEquals("Test the bus", result.getOrNull())
    }

    @Test
    fun `returns the failure a command's handler produced`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        stores.commandStore.registerHandlers(
            FailureCommand::class,
            listOf(
                CommandHandlerFactory(BrokenStateFailureCommandHandler::class) {
                    BrokenStateFailureCommandHandler()
                }
            ),
        )

        val bus = MessageBus(PersistingHandlerLocator(stores))

        val result = bus.execute(FailureCommand())

        assertEquals(
            FailureCommandFailure.BrokenStateFailure("Illegal state in command handling"),
            result.failureOrNull(),
        )
    }

    @Test
    fun `returns the value a query's handler produced`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        stores.queryStore.registerHandlers(
            StorageQuery::class,
            listOf(QueryHandlerFactory(StorageQueryHandler::class) { StorageQueryHandler() }),
        )

        val bus = MessageBus(PersistingHandlerLocator(stores))
        val list = mutableListOf("Test the bus")

        val result = bus.fetch(StorageQuery(0, list))

        assertTrue(result.isSuccess)
        assertEquals("Test the bus", result.getOrNull())
    }

    @Test
    fun `returns the failure a query's handler produced`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        stores.queryStore.registerHandlers(
            FailureQuery::class,
            listOf(QueryHandlerFactory(FailureQueryHandler::class) { FailureQueryHandler() }),
        )

        val bus = MessageBus(PersistingHandlerLocator(stores))

        val result = bus.fetch(FailureQuery())

        assertEquals(
            FailureCommandFailure.BrokenStateFailure("The query failed"),
            result.failureOrNull(),
        )
    }
}
