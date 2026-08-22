// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleBus01

import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.application.messages.command.CommandDependencies
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.QueryHandlerFactory
import com.jimbroze.kbus.example.fixtures.CreateUser
import com.jimbroze.kbus.example.fixtures.CreateUserHandler
import com.jimbroze.kbus.example.fixtures.GetUser
import com.jimbroze.kbus.example.fixtures.GetUserHandler

suspend fun main() {
    // Register handlers
    val stores = HandlerFactoryStoreCollection()
    stores.commandStore.registerHandlers(
        CreateUser::class,
        listOf(CommandHandlerFactory(CreateUserHandler::class) { _: CommandDependencies -> CreateUserHandler() })
    )
    stores.queryStore.registerHandlers(
        GetUser::class,
        listOf(QueryHandlerFactory(GetUserHandler::class) { GetUserHandler() })
    )

    // Create the bus
    val bus = MessageBus(PersistingHandlerLocator(stores))

    // Execute a command
    val result = bus.execute(CreateUser("Alice", "alice@example.com"))
    if (result.isSuccess) {
        println(result.getOrNull()) // "User Alice created"
    }

    // Fetch a query
    val userResult = bus.fetch(GetUser(1))
}
