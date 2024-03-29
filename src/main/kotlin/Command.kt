import kotlin.reflect.KClass

abstract class Command : Message() {
    override val messageType: String = "command"
}

interface CommandHandler<TCommand : Command> : MessageHandler<TCommand> {
    override suspend fun handle(message: TCommand): Any?
}

class TooManyHandlersException(
    message: String = "A handler has already been registered"
) : Exception(message) {
    constructor(messageCls: KClass<out Message>) : this(
        "A handler has already been registered for the message $messageCls"
    )
}

