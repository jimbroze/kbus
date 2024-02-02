import kotlin.reflect.KClass

class MessageStore<TMessageType : Message> {
    private var handlers = mutableMapOf<KClass<out TMessageType>, List<MessageHandler<out TMessageType>>>()

    fun <TMessage : TMessageType> registerHandlers(
        messageType: KClass<TMessage>,
        handlers: List<MessageHandler<TMessage>>,
    ) {
        this.handlers[messageType] = (this.handlers[messageType] ?: emptyList()) + handlers
    }

    fun <TMessage : TMessageType> removeHandlers(
        messageType: KClass<TMessage>,
        handlers: List<MessageHandler<TMessage>> = emptyList(),
    ) {
//            TODO handlers should be or allow set???

        val registeredHandlers =
            this.handlers[messageType]
                ?: throw MissingHandlerException(messageType)

        if (handlers.isNotEmpty()) {
            this.handlers[messageType] = registeredHandlers - handlers.toSet()
        } else {
            this.handlers.remove(messageType)
        }
    }

    fun <TMessage : TMessageType> isRegistered(messageType: KClass<TMessage>): Boolean {
        return handlers.contains(messageType)
    }

    suspend fun <TMessage : TMessageType> handle(
        message: TMessage,
        handlers: List<MessageHandler<TMessage>> = emptyList(),
    ): Any? {
        val matchedHandlers = getHandlers(message::class) + handlers

        return when (matchedHandlers.size) {
            1 -> matchedHandlers.first().handle(message)
            else -> {
                matchedHandlers.forEach { handler -> handler.handle(message) }
            }
        }
    }

    fun <TMessage : TMessageType> getHandlers(messageType: KClass<out TMessage>): List<MessageHandler<TMessage>> {
        @Suppress("UNCHECKED_CAST")
        return (handlers[messageType] ?: emptyList()) as List<MessageHandler<TMessage>>
    }
}
