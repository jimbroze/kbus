package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.annotations.Load
import com.jimbroze.kbus.core.*
import kotlinx.datetime.Clock


class TestGeneratorCommand(val messageData: String) : Command()

@Load
class TestGeneratorCommandHandler(
    private val locker: BusLocker,
    private val clock: Clock
) : CommandHandler<TestGeneratorCommand, Any, ResultFailure> {
    override suspend fun handle(message: TestGeneratorCommand): BusResult<Any, ResultFailure> {
        return success(message.messageData + clock.now().toString())
    }
}

class TestDuplicateGeneratorCommand(val messageData: String) : Command()

@Load
class TestDuplicateGeneratorCommandHandler(
    private val clock: Clock,
    private val bus: MessageBus,
) : CommandHandler<TestDuplicateGeneratorCommand, Any, ResultFailure> {
    override suspend fun handle(message: TestDuplicateGeneratorCommand): BusResult<Any, ResultFailure> {
        return success(message.messageData + clock.now().toString())
    }
}
