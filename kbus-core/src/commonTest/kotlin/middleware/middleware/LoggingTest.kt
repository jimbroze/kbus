package com.jimbroze.kbus.core.middleware.middleware

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.BusResult.Companion.success
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.core.registry.PrintEventHandler
import com.jimbroze.kbus.core.registry.StorageCommand
import com.jimbroze.kbus.core.registry.StorageCommandHandler
import com.jimbroze.kbus.core.registry.StorageEvent
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

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

class LoggingTest {

    @Test
    fun message_logger_does_not_log_messages_that_do_not_implement_logging_interface() = runTest {
        val captureLogger = CaptureLogger()
        val logger = MessageLogger(captureLogger, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR)

        logger.handle(StorageCommand("Testing", mutableListOf())) {
            StorageCommandHandler().handle(it)
        }

        assertEquals(0, captureLogger.logs.size)
    }

    @Test
    fun message_logger_logs_before_and_after_message_using_provided_level() = runTest {
        val captureLogger = CaptureLogger()
        val logger = MessageLogger(captureLogger, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR)

        logger.handle(LoggingLogCommand("Testing", captureLogger)) {
            LoggingLogCommandHandler().handle(it)
        }

        assertEquals(3, captureLogger.logs.size)
        assertContains(captureLogger.logs[0], "DEBUG: ")
        assertEquals("INFO: Testing", captureLogger.logs[1])
        assertContains(captureLogger.logs[2], "INFO: Successfully")
    }

    @Test
    fun test_commands_log_with_correct_verbs() = runTest {
        val captureLogger = CaptureLogger()
        val logger = MessageLogger(captureLogger, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR)

        logger.handle(LoggingLogCommand("Testing", captureLogger)) {
            LoggingLogCommandHandler().handle(it)
        }

        val allLogs = captureLogger.logs.joinToString(" | ")

        assertContains(allLogs, "Handling command")
        assertContains(allLogs, "handled command")
    }

    @Test
    fun test_queries_log_with_correct_verbs() = runTest {
        val captureLogger = CaptureLogger()
        val logger = MessageLogger(captureLogger, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR)

        logger.handle(LoggingLogQuery("Testing", captureLogger)) {
            LoggingLogQueryHandler().handle(it)
        }

        val allLogs = captureLogger.logs.joinToString(" | ")

        assertContains(allLogs, "Handling query")
        assertContains(allLogs, "handled query")
    }

    @Test
    fun test_commands_log_exception_and_rethrow() = runTest {
        val captureLogger = CaptureLogger()
        val logger = MessageLogger(captureLogger, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR)

        assertFailsWith<Exception> {
            logger.handle(LoggingExceptionCommand()) { ExceptionCommandHandler().handle(it) }
        }

        val allLogs = captureLogger.logs.joinToString(" | ")

        assertContains(allLogs, "ERROR: Failed handling")
        assertTrue(
            captureLogger.exceptions.any { it is Exception && it.message == "Exception raised" }
        )
    }

    @Test
    fun test_events_log_with_correct_verbs() = runTest {
        val captureLogger = CaptureLogger()
        val logger = MessageLogger(captureLogger, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR)

        logger.handle(LoggingStorageEvent("Testing", mutableListOf())) {
            PrintEventHandler().handle(it)
        }

        val allLogs = captureLogger.logs.joinToString(" | ")

        assertContains(allLogs, "Handling event")
        assertContains(allLogs, "handled event")
    }

    @Test
    fun test_events_log_exception_and_rethrow() = runTest {
        val captureLogger = CaptureLogger()
        val logger = MessageLogger(captureLogger, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR)

        assertFailsWith<Exception> {
            logger.handle(LoggingExceptionEvent()) { ExceptionEventHandler().handle(it) }
        }

        val allLogs = captureLogger.logs.joinToString(" | ")

        assertContains(allLogs, "ERROR: Failed handling")
    }
}
