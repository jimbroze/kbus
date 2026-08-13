package com.jimbroze.kbus.example.fixtures

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.api.result.FailureReason
import com.jimbroze.kbus.api.result.MessageFailure
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection

class MyCommand : Command<BusResult<String, MessageFailure>>()

class MyCommandHandler : CommandHandler<MyCommand, BusResult<String, MessageFailure>>() {
    override suspend fun handle(message: MyCommand): BusResult<String, MessageFailure> =
        BusResult.success("done")
}

class GenericMessageFailure(override val reason: FailureReason) : MessageFailure

val resultExampleStores = HandlerFactoryStoreCollection()
val resultExampleBus =
    MessageBus(PersistingHandlerLocator(resultExampleStores)).also {
        resultExampleStores.commandStore.registerHandlers(
            MyCommand::class,
            listOf(
                CommandHandlerFactory(MyCommandHandler::class) { _: CommandDependencies ->
                    MyCommandHandler()
                }
            ),
        )
    }
