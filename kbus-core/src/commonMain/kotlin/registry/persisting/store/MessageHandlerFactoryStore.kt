package com.jimbroze.kbus.core.registry.persisting.store

import com.jimbroze.kbus.api.common.Message
import com.jimbroze.kbus.api.common.MessageHandler
import com.jimbroze.kbus.api.messages.command.TooManyHandlersException
import kotlin.reflect.KClass

class MessageHandlerFactoryStore<TMessageType : Message> {
    private var factories =
        mutableMapOf<KClass<out TMessageType>, List<MessageHandlerFactory<out TMessageType, *>>>()

    fun <TMessage : TMessageType> registerHandlers(
        messageType: KClass<TMessage>,
        handlerFactories: List<MessageHandlerFactory<TMessage, *>>,
    ) {
        val existingFactories = this.factories[messageType] ?: listOf()

        val duplicateHandlerFactory =
            existingFactories.firstOrNull { existingFactory ->
                handlerFactories.any { factory ->
                    existingFactory.handlerType == factory.handlerType
                }
            }
        if (duplicateHandlerFactory !== null) {
            throw TooManyHandlersException(
                "A handler of type ${duplicateHandlerFactory.handlerType.simpleName} is already " +
                    "registered for this message."
            )
        }

        this.factories[messageType] = existingFactories + handlerFactories
    }

    fun <TMessage : TMessageType> removeHandlers(
        messageType: KClass<TMessage>,
        handlerTypes: List<KClass<out MessageHandler<TMessage>>>?,
    ) {
        if (handlerTypes === null) {
            this.factories.remove(messageType)
        } else {
            val registeredFactories = this.factories[messageType] ?: return

            this.factories[messageType] =
                registeredFactories.filterNot { factory ->
                    handlerTypes.any { type -> type == factory.handlerType }
                }
        }
    }

    /**
     * Every message type currently holding at least one handler factory. A type whose handlers have
     * all been removed is absent, so a caller never sees a registration that resolves to nothing.
     */
    fun registeredTypes(): Set<KClass<out TMessageType>> =
        factories.filterValues { it.isNotEmpty() }.keys.toSet()

    fun <TMessage : TMessageType> getHandlers(
        messageType: KClass<TMessage>
    ): List<MessageHandlerFactory<TMessage, *>> {
        @Suppress("UNCHECKED_CAST")
        return (factories[messageType] ?: emptyList()) as List<MessageHandlerFactory<TMessage, *>>
    }

    fun <TMessage : TMessageType, THandler : MessageHandler<TMessage>> getHandlersByType(
        handlerType: KClass<out THandler>
    ): List<MessageHandlerFactory<TMessage, THandler>> {
        val values = factories.values
        @Suppress("UNCHECKED_CAST")
        return values
            .flatten()
            .filter { it.handlerType == handlerType }
            .map { it as MessageHandlerFactory<TMessage, THandler> }
    }
}
