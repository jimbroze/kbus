import kotlin.jvm.JvmName
import kotlin.reflect.KClass

abstract class RuntimeDependencyLoader {
    abstract fun <TClass : Any> instantiate(cls: KClass<TClass>): TClass

    fun <TClass : Any> load(cls: KClass<TClass>): TClass {
        return instantiate(cls)
    }
}

class RuntimeLoadedMessageBus(
    middleware: List<Middleware>,
    val loader: RuntimeDependencyLoader,
) : MessageBus(middleware) {

    //    inline fun <reified TCommand : Command, TReturn : Any?, reified THandler : CommandHandler<TCommand, TReturn>> register(
//        messageType: KClass<TCommand>,
//        handlerType: KClass<THandler>,
//    ) {
//        requireNotNull(loader) { "No class loader provided to the message bus" }
//        register(messageType, loader.load(handlerType))
//    }

    @JvmName("registerTypes")
    inline fun <reified TEvent : Event, reified THandler : EventHandler<TEvent>> register(
        messageType: KClass<TEvent>,
        handlerTypes: List<KClass<THandler>>,
    ) {
        val loadedHandlers = handlerTypes.map { loader.load(it) }
        register(messageType, loadedHandlers)
    }

    suspend fun <TCommand : Command, TReturn : Any?> execute(
        command: TCommand,
        handlerType: KClass<CommandHandler<TCommand, TReturn>>,
    ): TReturn {
        val handler = loader.load(handlerType)
        return this.execute(command, handler)
    }
}
