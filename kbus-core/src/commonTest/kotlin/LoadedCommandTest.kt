import kotlinx.datetime.Clock

class UnloadedCommand(val messageData: String) : Command()

class UnloadedCommandHandler(val clock: Clock) : CommandHandler<UnloadedCommand, Any> {
    override suspend fun handle(message: UnloadedCommand): Any {
        return message.messageData
    }
}

class UnloadedCommandLoaded(messageData: String) {
    val command = UnloadedCommand(messageData)

    suspend fun handle(handler: UnloadedCommandHandler) = handler.handle(command)
}

interface GeneratedDependencies {
    fun getClock(): Clock
}
class GeneratedLoader(private val dependencies: GeneratedDependencies) {

    fun getReturnCommandHandler(): ReturnCommandHandler {
        return ReturnCommandHandler()
    }
    fun getUnloadedCommandHandler(): UnloadedCommandHandler {
        return UnloadedCommandHandler(this.dependencies.getClock())
    }
}

suspend fun MessageBus.execute(loadedCommand: UnloadedCommandLoaded) {
//    val handler: UnloadedCommandHandler = this.loader.getHandler<UnloadedCommandHandler>()
    val handler: UnloadedCommandHandler = GeneratedLoader().getHandler<UnloadedCommandHandler>()
    this.execute(loadedCommand.command, handler)
}

class LoadedCommandTest {
}
