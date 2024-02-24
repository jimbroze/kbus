package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.annotations.Load
import com.jimbroze.kbus.core.BusLocker
import com.jimbroze.kbus.core.Command
import com.jimbroze.kbus.core.CommandHandler
import com.jimbroze.kbus.core.MessageBus
import kotlinx.datetime.Clock


class TestGeneratorCommand(val messageData: String) : Command()

@Load
class TestGeneratorCommandHandler(
    private val locker: BusLocker,
    private val clock: Clock
) : CommandHandler<TestGeneratorCommand, Any> {
    override suspend fun handle(message: TestGeneratorCommand): Any {
        return message.messageData + clock.now().toString()
    }
}

class TestDuplicateGeneratorCommand(val messageData: String) : Command()

@Load
class TestDuplicateGeneratorCommandHandler(
    private val clock: Clock,
    private val bus: MessageBus,
) : CommandHandler<TestDuplicateGeneratorCommand, Any> {
    override suspend fun handle(message: TestDuplicateGeneratorCommand): Any {
        return message.messageData + clock.now().toString()
    }
}
