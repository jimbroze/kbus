abstract class Command : Message() {
    override val messageType: String = "command"
}

interface CommandHandler<TCommand : Command> : MessageHandler<TCommand> {
    override fun handle(message: TCommand): Any?
}

class TooManyHandlersException(message: String) : Exception(message)

