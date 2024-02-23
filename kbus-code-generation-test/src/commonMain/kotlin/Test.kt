package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.annotations.Load
import com.jimbroze.kbus.core.Command
import com.jimbroze.kbus.core.CommandHandler
import kotlinx.datetime.Clock


class GeneratorCommand(val messageData: String) : Command()

@Load
class GeneratorCommandHandler(private val clock: Clock) : CommandHandler<GeneratorCommand, Any> {
    override suspend fun handle(message: GeneratorCommand): Any {
        return message.messageData + clock.now().toString()
    }
}
