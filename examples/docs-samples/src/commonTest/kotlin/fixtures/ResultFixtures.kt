package com.jimbroze.kbus.example.fixtures

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.api.result.MessageFailure
import com.jimbroze.kbus.application.messages.command.CommandDependencies
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection

sealed interface CancelSubscriptionFailure : MessageFailure {
    data class NotFound(val subscriptionId: String) : CancelSubscriptionFailure

    data class AlreadyCancelled(val subscriptionId: String) : CancelSubscriptionFailure
}

class CancelSubscription(val subscriptionId: String) :
    Command<BusResult<String, CancelSubscriptionFailure>>()

class CancelSubscriptionHandler :
    CommandHandler<CancelSubscription, BusResult<String, CancelSubscriptionFailure>>() {
    override suspend fun handle(
        message: CancelSubscription
    ): BusResult<String, CancelSubscriptionFailure> =
        BusResult.success("Cancelled ${message.subscriptionId}")
}

val resultExampleStores = HandlerFactoryStoreCollection()
val resultExampleBus =
    MessageBus(PersistingHandlerLocator(resultExampleStores)).also {
        resultExampleStores.commandStore.registerHandlers(
            CancelSubscription::class,
            listOf(
                CommandHandlerFactory(CancelSubscriptionHandler::class) { _: CommandDependencies ->
                    CancelSubscriptionHandler()
                }
            ),
        )
    }
