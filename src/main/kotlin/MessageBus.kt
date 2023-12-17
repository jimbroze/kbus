import kotlin.reflect.KClass

class MessageBus(val middlewares: List<Middleware> = emptyList()) {
    private val commandStore: MessageStore<Command> = MessageStore()
    private val eventStore: MessageStore<Event> = MessageStore()

    fun <TCommand : Command> execute(
        command: TCommand,
        handler: CommandHandler<TCommand>? = null,
    ): Any?
    {
        checkForCommandHandler(command::class, handler)

        val commandBus = getBus(commandStore, listOfNotNull(handler))
        return commandBus(command)

//        return this.commandBus.handle(command, listOfNotNull(handler))
    }

    fun <TEvent : Event> dispatch(
        event: TEvent,
        handlers: List<EventHandler<TEvent>> = emptyList(),
    ) {
        val eventBus = getBus(eventStore, handlers)
        eventBus(event)

//        this.eventBus.handle(event, handlers)
    }

    fun <TCommand : Command> register(
        messageType: KClass<TCommand>,
        handler: CommandHandler<TCommand>,
    ) {
        checkForCommandHandler(messageType, handler)

        this.commandStore.registerHandlers(messageType, listOfNotNull(handler))
    }

    fun <TEvent : Event> register(
        eventType: KClass<TEvent>,
        handlers: List<EventHandler<TEvent>>,
    ) {
        this.eventStore.registerHandlers(eventType, handlers)
    }
    fun <TCommand : Command> deregister(commandType: KClass<TCommand>) {
        this.commandStore.removeHandlers(commandType)
    }

    fun <TEvent : Event> deregister(
        messageType: KClass<TEvent>,
        handlers: List<EventHandler<TEvent>> = emptyList()
    ) {
        this.eventStore.removeHandlers(messageType, handlers)
    }
    fun <TCommand : Command> isRegistered(commandType: KClass<TCommand>): Boolean {
        return this.commandStore.isRegistered(commandType)
    }

    fun <TEvent : Event> hasHandlers(eventType: KClass<TEvent>): Int {
        return this.eventStore.getHandlers(eventType).size
    }

    private fun <TCommand : Command> checkForCommandHandler(
        commandType: KClass<out TCommand>,
        handler: CommandHandler<out TCommand>?
    ) {
        when {
            handler != null && commandStore.isRegistered(commandType) ->
                throw TooManyHandlersException(commandType)

            handler == null && !commandStore.isRegistered(commandType) ->
                throw MissingHandlerException(commandType)
        }
    }

    private fun <TMessage : Message> getBus(
        store: MessageStore<in TMessage>,
        handlers: List<MessageHandler<TMessage>>
    ): (TMessage) -> Any? {
        val finalHandler = { message: TMessage -> store.handle(message, handlers) }
        val bus = createMiddlewareChain(finalHandler, middlewares)
        return bus
    }
}
