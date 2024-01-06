import kotlin.reflect.KClass

class MessageBus(val middlewares: List<Middleware> = emptyList(), val loader: DependencyLoader? = null) {
    private val commandStore: MessageStore<Command> = MessageStore()
    private val eventStore: MessageStore<Event> = MessageStore()

    suspend fun <TCommand : Command> execute(
        command: TCommand,
        handler: CommandHandler<TCommand>? = null,
    ): Any?
    {
        checkForCommandHandler(command::class, handler)

        val commandBus = getBus(commandStore, listOfNotNull(handler))
        return commandBus(command)
    }

    suspend fun <TEvent : Event> dispatch(
        event: TEvent,
        handlers: List<EventHandler<TEvent>> = emptyList(),
    ) {
        val eventBus = getBus(eventStore, handlers)
        eventBus(event)
    }

    inline fun <reified TCommand : Command, reified THandler : CommandHandler<TCommand>> register(
        messageType: KClass<TCommand>,
        handlerType: KClass<THandler>,
    ) {
        requireNotNull(loader) {"No class loader provided to the message bus"}
        register(messageType, loader.load(handlerType))
    }

    fun <TCommand : Command> register(
        messageType: KClass<TCommand>,
        handler: CommandHandler<TCommand>,
    ) {
        checkForCommandHandler(messageType, handler)

        this.commandStore.registerHandlers(messageType, listOfNotNull(handler))
    }

    @JvmName("registerTypes")
    inline fun <reified TEvent : Event, reified THandler : EventHandler<TEvent>> register(
        messageType: KClass<TEvent>,
        handlerTypes: List<KClass<THandler>>,
    ) {
        requireNotNull(loader) {"No class loader provided to the message bus"}

        val loadedHandlers = handlerTypes.map { loader.load(it) }
        register(messageType, loadedHandlers)
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

    private suspend fun <TMessage : Message> getBus(
        store: MessageStore<in TMessage>,
        handlers: List<MessageHandler<TMessage>>
    ): MiddlewareHandler<TMessage> {
        val finalHandler: suspend (TMessage) -> Any? = { message: TMessage ->
            store.handle(message, handlers)
        }
        val bus = createMiddlewareChain(finalHandler, middlewares)

        return bus
    }
}
