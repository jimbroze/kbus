package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.api.messages.event.Event
import com.jimbroze.kbus.api.messages.event.EventHandler
import com.jimbroze.kbus.api.messages.query.Query
import com.jimbroze.kbus.api.messages.query.QueryHandler
import com.jimbroze.kbus.api.middleware.LoggingMessage
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.api.result.BusResult.Companion.success
import com.jimbroze.kbus.api.result.MessageFailure
import com.jimbroze.kbus.api.uow.TransactionConfig
import com.jimbroze.kbus.infrastructure.logging.LogLevel
import com.jimbroze.kbus.infrastructure.logging.Logger

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
    override val executeInTransaction: TransactionConfig? = null

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
