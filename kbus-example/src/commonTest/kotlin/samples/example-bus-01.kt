// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleBus01

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.registry.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.QueryHandlerFactory

class CreateUser(val name: String, val email: String) :
    Command<BusResult<String, MessageFailure>>()

class CreateUserHandler :
    CommandHandler<CreateUser, BusResult<String, MessageFailure>>() {
    override suspend fun handle(message: CreateUser): BusResult<String, MessageFailure> =
        BusResult.success("User ${message.name} created")
}

class GetUser(val id: Int) :
    Query<BusResult<String, MessageFailure>>()

class GetUserHandler :
    QueryHandler<GetUser, BusResult<String, MessageFailure>>() {
    override suspend fun handle(message: GetUser): BusResult<String, MessageFailure> =
        BusResult.success("User #${message.id}")
}

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
