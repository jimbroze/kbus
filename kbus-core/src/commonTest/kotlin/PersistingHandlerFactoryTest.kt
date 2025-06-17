package com.jimbroze.kbus.core

import kotlin.test.Test
import kotlin.test.assertIs

class PersistingHandlerFactoryTest {
    @Test
    fun test_it_creates_command_handler() {
        val handlerType = StorageCommandHandler::class
        val stores = HandlerFactoryStoreCollection()
        val factory = PersistingHandlerFactory(stores)

        stores.commandStore.registerHandlers(
            StorageCommand::class,
            listOf(TestMessageHandlerFactory(handlerType) { StorageCommandHandler() }),
        )

        val handler = factory.create(handlerType)
        assertIs<StorageCommandHandler>(handler)
    }

    @Test
    fun test_it_creates_query_handler() {
        val handlerType = StorageQueryHandler::class
        val stores = HandlerFactoryStoreCollection()
        val factory = PersistingHandlerFactory(stores)

        stores.queryStore.registerHandlers(
            StorageQuery::class,
            listOf(TestMessageHandlerFactory(handlerType) { StorageQueryHandler() }),
        )

        val handler = factory.create(handlerType)
        assertIs<StorageQueryHandler>(handler)
    }
}
