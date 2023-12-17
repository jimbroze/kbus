abstract class Event : Message() {
    override val messageType: String = "event"
}

interface EventHandler<TEvent : Event> : MessageHandler<TEvent> {
    override fun handle(message: TEvent)
}
