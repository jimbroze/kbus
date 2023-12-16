import kotlin.reflect.KClass

class MessageBus {
    private val commandBus: MessageStore<Command> = MessageStore()

    fun <TCommand : Command> execute(
        command: TCommand,
        handler: CommandHandler<TCommand>? = null,
    ): Any?
    {
//        val busClosure = { c: TCommand -> this.commandBus.execute(c, handler) }
//        bus = create_middleware_chain(self.middleware, busClosure)
//        return bus(command)

        return this.commandBus.execute(command, listOfNotNull(handler))
    }

//    def dispatch(
//    self, event: EventT, handlers: Sequence[EventHandler[EventT]] | None = None
//    ) -> None:
//    """Forwards an event to an EventBus for dispatching.
//
//        Example:
//            >>> from tests.examples import ExampleEvent, ExampleEventHandler
//            >>> test_bus = MessageBus()
//            >>> test_handler = ExampleEventHandler()
//            >>> test_event = ExampleEvent("Testing...")
//            >>>
//            >>> test_bus.dispatch(test_event, [test_handler])
//            Testing...
//        """
//
//    def bus_closure(e: EventT) -> None:
//    return self.event_bus.dispatch(e, handlers)
//
//    # noinspection PyTypeChecker
//    bus = create_middleware_chain(bus_closure, self.middleware)
//    bus(event)

//    fun <TCommand : Command> register(
//        messageType: KClass<TCommand>,
//        handler: KClass<CommandHandler<TCommand>>,
//    ) {
//        val loadedHandler = this.loader.load(handler)
//        this.register(messageType, loadedHandler)
//    }

    fun <TCommand : Command> register(
        messageType: KClass<TCommand>,
        handler: CommandHandler<TCommand>,
    ) {
        this.commandBus.registerHandlers(messageType, listOfNotNull(handler))
    }

    fun <TCommand : Command> deregister(messageType: KClass<TCommand>) {
        this.commandBus.removeHandlers(messageType)
    }


//    fun <TCommand : Command> hasHandlers(eventType: KClass<TCommand>): int {
//        return this.eventBus.hasHandlers(event_type)
//    }

    fun <TCommand : Command> isRegistered(commandType: KClass<TCommand>): Boolean {
        return this.commandBus.isRegistered(commandType)
    }
}
