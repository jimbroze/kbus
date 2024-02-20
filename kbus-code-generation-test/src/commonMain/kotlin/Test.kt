package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.annotations.Load
import com.jimbroze.kbus.core.Command
import com.jimbroze.kbus.core.CommandHandler
import kotlinx.datetime.Clock


class GeneratorCommand(val messageData: String, val clock: Clock) : Command()

@Load
class GeneratorCommandHandler(val clock: Clock) : CommandHandler<GeneratorCommand, Any> {
    override suspend fun handle(message: GeneratorCommand): Any {
        return message.messageData
    }
}

fun main() {
//    println(GeneratorCommandLoaded::class.qualifiedName)
    println("Hello world!")
}
