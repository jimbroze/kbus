package com.jimbroze.kbus.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TestLoadedCommand(override val command: StorageCommand) :
    LoadedCommand<StorageCommand, StorageCommandHandler, Unit, GenericFailure> {
    override val handler = StorageCommandHandler::class
}

class TestLoadedQuery(override val query: StorageQuery) :
    LoadedQuery<StorageQuery, StorageQueryHandler, String, GenericFailure> {
    override val handler = StorageQueryHandler::class
}

class LoadedMessageBusTest {
    @Test
    fun test_it_executes_loaded_command() = runTest {
        val handlerLocator = PersistingHandlerLocator()
        val mapper = handlerLocator.messageMapper as PersistingHandlerMapper
        val loadedBus = LoadedMessageBus(handlerLocator, emptyList())
        val list = mutableListOf<String>()
        val loadedCommand = TestLoadedCommand(StorageCommand("Test loaded command", list))

        mapper.register(
            StorageCommand::class,
            TestMessageHandlerFactory(StorageCommandHandler::class) { StorageCommandHandler() },
        )

        val result = loadedBus.execute(loadedCommand)

        assertTrue(result.isSuccess)
        assertContains(list, "Test loaded command")
    }

    @Test
    fun test_it_fetches_loaded_query() = runTest {
        val handlerLocator = PersistingHandlerLocator()
        val mapper = handlerLocator.messageMapper as PersistingHandlerMapper
        val loadedBus = LoadedMessageBus(handlerLocator, emptyList())
        val list = mutableListOf("Test loaded query")
        val query = StorageQuery(0, list)
        val loadedQuery = TestLoadedQuery(query)

        mapper.register(
            StorageQuery::class,
            TestMessageHandlerFactory(StorageQueryHandler::class) { StorageQueryHandler() },
        )

        val result = loadedBus.fetch(loadedQuery)

        assertTrue(result.isSuccess)
        assertEquals("Test loaded query", result.getOrNull())
    }
}
