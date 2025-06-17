package com.jimbroze.kbus.core

import kotlin.reflect.KClass

interface MessageHandlerFactory<TMessage : Message, THandler : MessageHandler<TMessage>> {
    val handlerType: KClass<THandler>

    fun create(): THandler
}

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
                duplicateHandlerFactory.handlerType as KClass<out MessageHandler<*>>
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

    fun <TMessage : TMessageType> isRegistered(messageType: KClass<TMessage>): Boolean {
        return factories.contains(messageType)
    }

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
