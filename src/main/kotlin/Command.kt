import kotlin.reflect.KClass

abstract class Command : Message() {
    override val messageType: String = "command"
}

interface CommandHandler<T : Command> {
    fun handle(command: T): Any?
}

class TooManyHandlersException(message: String) : Exception(message)

class CommandBus {
    private val handlers = mutableMapOf<KClass<out Command>, CommandHandler<*>>()

    fun <TCommand : Command> registerHandler(
        commandType: KClass<TCommand>,
        handler: CommandHandler<TCommand>,
    ) {
        handlers[commandType] = handler
    }

    fun <TCommand : Command> removeHandler(commandType: KClass<TCommand>) {
        handlers.remove(commandType) ?: throw MissingHandlerException()
    }

    fun <TCommand : Command> isRegistered(commandType: KClass<TCommand>): Boolean {
        return handlers.contains(commandType)
    }

    fun <TCommand : Command> execute(
        command: TCommand,
        handler: CommandHandler<TCommand>? = null,
    ): Any? {
        val commandName = command.toString()

        val matchedHandler =
            getHandler(command)
                ?: handler
                ?: throw MissingHandlerException("A handler has not been registered for the command $commandName")

        if (handler != null && handler != matchedHandler) {
            throw TooManyHandlersException("A handler has already been registered for the command $commandName")
        }

        return matchedHandler.handle(command)
    }

    private fun <TCommand : Command> getHandler(command: TCommand): CommandHandler<TCommand>? {
        @Suppress("UNCHECKED_CAST")
        return handlers[command::class] as CommandHandler<TCommand>?
    }
}
