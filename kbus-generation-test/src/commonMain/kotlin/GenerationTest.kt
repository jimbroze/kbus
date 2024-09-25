package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.annotations.Load
import com.jimbroze.kbus.core.BusLocker
import com.jimbroze.kbus.core.BusResult
import com.jimbroze.kbus.core.Command
import com.jimbroze.kbus.core.CommandHandler
import com.jimbroze.kbus.core.FailureReason
import com.jimbroze.kbus.core.MessageBus
import com.jimbroze.kbus.core.Query
import com.jimbroze.kbus.core.QueryHandler
import kotlinx.datetime.Clock

class TestGeneratorCommand(val messageData: String) : Command()

@Load
class TestGeneratorCommandHandler(private val locker: BusLocker, private val clock: Clock) :
    CommandHandler<TestGeneratorCommand, Any, FailureReason> {
    override suspend fun handle(message: TestGeneratorCommand): BusResult<Any, FailureReason> {
        locker.toString()
        return success(message.messageData + clock.now().toString())
    }
}

class TestDuplicateGeneratorCommand(val messageData: String) : Command()

@Load
class TestDuplicateGeneratorCommandHandler(private val clock: Clock, private val bus: MessageBus) :
    CommandHandler<TestDuplicateGeneratorCommand, Any, FailureReason> {
    override suspend fun handle(
        message: TestDuplicateGeneratorCommand
    ): BusResult<Any, FailureReason> {
        bus.toString()
        return success(message.messageData + clock.now().toString())
    }
}

class TestGeneratorQuery(val messageData: String) : Query()

@Load
class TestGeneratorQueryHandler(private val locker: BusLocker, private val clock: Clock) :
    QueryHandler<TestGeneratorQuery, Any, FailureReason> {
    override suspend fun handle(message: TestGeneratorQuery): BusResult<Any, FailureReason> {
        locker.toString()
        return success(message.messageData + clock.now().toString())
    }
}
