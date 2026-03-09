package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.BusResult.Companion.success
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.core.middleware.middleware.LogLevel
import com.jimbroze.kbus.core.middleware.middleware.Logger
import com.jimbroze.kbus.core.middleware.middleware.LoggingMessage

internal enum class LogLevels(override val level: String) : LogLevel {
    DEBUG("DEBUG"),
    INFO("INFO"),
    ERROR("ERROR"),
}

class CaptureLogger : Logger {
    val logs = mutableListOf<String>()
    val exceptions = mutableListOf<Throwable>()

    override fun log(level: LogLevel, message: String, exception: Throwable?) {
        logs.add(level.level + ": " + message)
        if (exception != null) {
            exceptions.add(exception)
        }
    }
}

class OrderCaptureLogger : Logger {
    val logs = mutableListOf<Int>()

    override fun log(level: LogLevel, message: String, exception: Throwable?) {
        logs.add(orderCounter++)
    }

    companion object {
        var orderCounter = 0
    }
}

class LoggingLogCommand(val messageToLog: String, val logger: Logger) :
    Command<BusResult<Unit, MessageFailure>>(), LoggingMessage

class LoggingLogCommandHandler :
    CommandHandler<LoggingLogCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: LoggingLogCommand): BusResult<Unit, MessageFailure> {
        message.logger.log(LogLevels.INFO, message.messageToLog, null)
        return success(Unit)
    }
}

class LoggingLogQuery(val messageToLog: String, val logger: Logger) :
    Query<BusResult<Unit, MessageFailure>>(), LoggingMessage

class LoggingLogQueryHandler : QueryHandler<LoggingLogQuery, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: LoggingLogQuery): BusResult<Unit, MessageFailure> {
        message.logger.log(LogLevels.INFO, message.messageToLog, null)
        return success(Unit)
    }
}

class LoggingStorageEvent(message: String, listStore: MutableList<String>) :
    StorageEvent(message, listStore), LoggingMessage

class TestException(message: String) : Exception(message)

class LoggingExceptionCommand : Command<BusResult<Unit, MessageFailure>>(), LoggingMessage

class ExceptionCommandHandler :
    CommandHandler<Command<BusResult<Unit, MessageFailure>>, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(
        message: Command<BusResult<Unit, MessageFailure>>
    ): BusResult<Unit, MessageFailure> {
        throw TestException("Exception raised")
    }
}

class LoggingExceptionEvent : Event(), LoggingMessage

class ExceptionEventHandler : EventHandler<Event> {
    override suspend fun handle(message: Event) {
        throw TestException("Exception raised")
    }
}
