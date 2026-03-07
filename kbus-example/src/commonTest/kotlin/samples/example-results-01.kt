// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleResults01

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.registry.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.PersistingHandlerLocator

class MyCommand : Command<BusResult<String, MessageFailure>>()

class MyCommandHandler : CommandHandler<MyCommand, BusResult<String, MessageFailure>>() {
    override suspend fun handle(message: MyCommand): BusResult<String, MessageFailure> =
        BusResult.success("done")
}

val stores = HandlerFactoryStoreCollection()
val bus = MessageBus(PersistingHandlerLocator(stores)).also {
    stores.commandStore.registerHandlers(
        MyCommand::class,
        listOf(CommandHandlerFactory(MyCommandHandler::class) { _: CommandDependencies -> MyCommandHandler() })
    )
}

suspend fun main() {
    val result: BusResult<String, MessageFailure> = bus.execute(MyCommand())

    when {
        result.isSuccess -> println("Value: ${result.getOrNull()}")
        result.isFailure -> println("Error: ${result.failureOrNull()?.reason?.message}")
    }
}
