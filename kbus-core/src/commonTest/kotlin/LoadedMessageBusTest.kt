package com.jimbroze.kbus.core

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TestLoadedCommand(override val command: StorageCommand) :
    LoadedCommand<
        StorageCommand,
        CommandHandler<StorageCommand, Unit, GenericFailure>,
        Unit,
        GenericFailure,
    > {
    override val handler =
        StorageCommandHandler::class as KClass<CommandHandler<StorageCommand, Unit, GenericFailure>>
}

class TestLoadedQuery(override val query: StorageQuery) :
    LoadedQuery<
        StorageQuery,
        QueryHandler<StorageQuery, String, GenericFailure>,
        String,
        GenericFailure,
    > {
    override val handler =
        StorageQueryHandler::class as KClass<QueryHandler<StorageQuery, String, GenericFailure>>
}

class LoadedMessageBusTest {
    @Test
    fun test_it_executes_loaded_command() = runTest {
        val handlerLocator = PersistingHandlerLocator()
        val mapper = handlerLocator.messageMapper as PersistingHandlerMapper
        val loadedBus = LoadedMessageBus(handlerLocator, EmptyTransactionManager(), emptyList())
        val list = mutableListOf<String>()
        val loadedCommand = TestLoadedCommand(StorageCommand("Test loaded command", list))

        mapper.register(
            StorageCommand::class,
            CommandHandlerFactory(StorageCommandHandler::class) { StorageCommandHandler() },
        )

        val result = loadedBus.execute(loadedCommand)

        assertTrue(result.isSuccess)
        assertContains(list, "Test loaded command")
    }

    @Test
    fun test_it_fetches_loaded_query() = runTest {
        val handlerLocator = PersistingHandlerLocator()
        val mapper = handlerLocator.messageMapper as PersistingHandlerMapper
        val loadedBus = LoadedMessageBus(handlerLocator, EmptyTransactionManager(), emptyList())
        val list = mutableListOf("Test loaded query")
        val query = StorageQuery(0, list)
        val loadedQuery = TestLoadedQuery(query)

        mapper.register(
            StorageQuery::class,
            QueryHandlerFactory(StorageQueryHandler::class) { StorageQueryHandler() },
        )

        val result = loadedBus.fetch(loadedQuery)

        assertTrue(result.isSuccess)
        assertEquals("Test loaded query", result.getOrNull())
    }
}
