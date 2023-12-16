import kotlin.reflect.KClass

class MessageStore<TMessageType : Message> {
    private var handlers = mutableMapOf<KClass<out TMessageType>, List<MessageHandler<out TMessageType>>>()

    fun <TMessage : TMessageType> registerHandlers(
        messageType: KClass<TMessage>,
        handlers: List<MessageHandler<TMessage>>,
    ) {
        this.handlers[messageType] = this.handlers.getOrDefault(messageType, emptyList()) + handlers
    }

    fun <TMessage : TMessageType> removeHandlers(
        messageType: KClass<TMessage>,
        handlers: List<MessageHandler<TMessage>> = emptyList(),
    ) {
//            TODO handlers should be or allow set???

        val registeredHandlers = this.handlers[messageType] ?: throw MissingHandlerException()
        if (handlers.isNotEmpty()) {
            this.handlers[messageType] = registeredHandlers - handlers.toSet()
        } else {
            this.handlers.remove(messageType)
        }
    }

    fun <TMessage : TMessageType> isRegistered(messageType: KClass<TMessage>): Boolean {
        return handlers.contains(messageType)
    }

    fun <TMessage : TMessageType> execute(
        message: TMessage,
        handlers: List<MessageHandler<TMessage>> = emptyList(),
    ): Any? {
//        val messageName = message.toString()
//
//        val matchedHandler =
//            getHandler(message)
//                ?: handler
//                ?: throw MissingHandlerException("A handler has not been registered for the message $messageName")
//
//        if (handler != null && handler != matchedHandler) {
//            throw TooManyHandlersException("A handler has already been registered for the message $messageName")
//        }
//
//        return matchedHandler.handle(message)
//
//        if (handlers === null) { handlers = [] }

        val matchedHandlers = getHandlers(message) + handlers

        return when (matchedHandlers.size) {
            0 -> throw MissingHandlerException()
            1 -> matchedHandlers.first().handle(message)
            else -> {
                matchedHandlers.forEach { handler -> handler.handle(message) }
            }
        }
    }

    private fun <TMessage : TMessageType> getHandlers(message: TMessage): List<MessageHandler<TMessage>> {
        @Suppress("UNCHECKED_CAST")
        return handlers.getOrDefault(message::class, emptyList()) as List<MessageHandler<TMessage>>
    }
}
