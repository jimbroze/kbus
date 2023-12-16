import kotlin.reflect.KClass

abstract class Command : Message() {
    override val messageType: String = "command"
}

interface CommandHandler<TCommand : Command> : MessageHandler<TCommand> {
    override fun handle(message: TCommand): Any?
}

class TooManyHandlersException(message: String) : Exception(message)

class MessageStore<TMessageType : Message> {
    private val handlers = mutableMapOf<KClass<out TMessageType>, MessageHandler<*>>()

    fun <TMessage : TMessageType> registerHandler(
        messageType: KClass<TMessage>,
        handler: MessageHandler<TMessage>,
    ) {
        handlers[messageType] = handler
    }

    fun <TMessage : TMessageType> removeHandler(messageType: KClass<TMessage>) {
        handlers.remove(messageType) ?: throw MissingHandlerException()
    }

    fun <TMessage : TMessageType> isRegistered(messageType: KClass<TMessage>): Boolean {
        return handlers.contains(messageType)
    }

    fun <TMessage : TMessageType> execute(
        message: TMessage,
        handler: MessageHandler<TMessage>? = null,
    ): Any? {
        val messageName = message.toString()

        val matchedHandler =
            getHandler(message)
                ?: handler
                ?: throw MissingHandlerException("A handler has not been registered for the message $messageName")

        if (handler != null && handler != matchedHandler) {
            throw TooManyHandlersException("A handler has already been registered for the message $messageName")
        }

        return matchedHandler.handle(message)
    }

    private fun <TMessage : TMessageType> getHandler(message: TMessage): MessageHandler<TMessage>? {
        @Suppress("UNCHECKED_CAST")
        return handlers[message::class] as MessageHandler<TMessage>?
    }
}
