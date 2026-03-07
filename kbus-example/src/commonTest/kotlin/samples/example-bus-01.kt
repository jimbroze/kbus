// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleBus01

// Register handlers
val stores = HandlerFactoryStoreCollection()
stores.commandStore.registerHandlers(
    CreateUser::class,
    listOf(CommandHandlerFactory(CreateUserHandler::class) { CreateUserHandler() })
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
