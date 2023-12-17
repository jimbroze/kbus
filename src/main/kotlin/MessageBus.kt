import kotlin.reflect.KClass

class MessageBus {
    private val commandBus: MessageStore<Command> = MessageStore()
    private val eventBus: MessageStore<Event> = MessageStore()

    fun <TCommand : Command> execute(
        command: TCommand,
        handler: CommandHandler<TCommand>? = null,
    ): Any?
    {
//        val busClosure = { c: TCommand -> this.commandBus.execute(c, handler) }
//        bus = create_middleware_chain(self.middleware, busClosure)
//        return bus(command)

        checkForCommandHandler(command::class, handler)

        return this.commandBus.handle(command, listOfNotNull(handler))
    }

    fun <TEvent : Event> dispatch(
        event: TEvent,
        handlers: List<EventHandler<TEvent>> = emptyList(),
    ) {
//        val busClosure = { c: TCommand -> this.commandBus.execute(c, handler) }
//        bus = create_middleware_chain(self.middleware, busClosure)
//        return bus(command)

        this.eventBus.handle(event, handlers)
    }

    fun <TCommand : Command> register(
        messageType: KClass<TCommand>,
        handler: CommandHandler<TCommand>,
    ) {
        checkForCommandHandler(messageType, handler)

        this.commandBus.registerHandlers(messageType, listOfNotNull(handler))
    }
    fun <TEvent : Event> register(
        eventType: KClass<TEvent>,
        handlers: List<EventHandler<TEvent>>,
    ) {
        this.eventBus.registerHandlers(eventType, handlers)
    }

    fun <TCommand : Command> deregister(commandType: KClass<TCommand>) {
        this.commandBus.removeHandlers(commandType)
    }
    fun <TEvent : Event> deregister(
        messageType: KClass<TEvent>,
        handlers: List<EventHandler<TEvent>> = emptyList()
    ) {
        this.eventBus.removeHandlers(messageType, handlers)
    }

    fun <TCommand : Command> isRegistered(commandType: KClass<TCommand>): Boolean {
        return this.commandBus.isRegistered(commandType)
    }

    fun <TEvent : Event> hasHandlers(eventType: KClass<TEvent>): Int {
        return this.eventBus.getHandlers(eventType).size
    }

    private fun <TCommand : Command> checkForCommandHandler(
        commandType: KClass<out TCommand>,
        handler: CommandHandler<out TCommand>?
    ) {
        when {
            handler != null && commandBus.isRegistered(commandType) ->
                throw TooManyHandlersException("A handler has already been registered for the command $commandType")

            handler == null && !commandBus.isRegistered(commandType) ->
                throw MissingHandlerException()
        }
    }
}
