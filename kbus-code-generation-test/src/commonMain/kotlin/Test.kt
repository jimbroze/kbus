import java.time.Clock

//import GeneratorCommandLoaded

class GeneratorCommand(val messageData: String, val clock: Clock) : Command()

@Load
class GeneratorCommandHandler(val clock: Clock) : CommandHandler<GeneratorCommand, Any> {
    override suspend fun handle(message: GeneratorCommand): Any {
        return message.messageData
    }
}

fun main() {
//    println(GeneratorCommandLoaded)
    println("Hello world!")
}
