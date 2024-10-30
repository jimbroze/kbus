package com.jimbroze.kbus.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.TimeSource
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

class TimeCaptureLogger : Logger {
    private val timeSource = TimeSource.Monotonic
    val logs = mutableListOf<TimeSource.Monotonic.ValueTimeMark>()

    override fun log(level: LogLevel, message: String, exception: Throwable?) {
        logs.add(timeSource.markNow())
    }
}

class LoggingLogCommand(val messageToLog: String, val logger: Logger) : Command(), LoggingCommand

class LoggingLogCommandHandler : CommandHandler<LoggingLogCommand, Unit, FailureReason>() {
    override suspend fun handle(message: LoggingLogCommand): BusResult<Unit, FailureReason> {
        message.logger.log(LogLevels.INFO, message.messageToLog, null)
        return success()
    }
}

class LoggingLogQuery(val messageToLog: String, val logger: Logger) : Query(), LoggingQuery

class LoggingLogQueryHandler : QueryHandler<LoggingLogQuery, Unit, FailureReason> {
    override suspend fun handle(message: LoggingLogQuery): BusResult<Unit, FailureReason> {
        message.logger.log(LogLevels.INFO, message.messageToLog, null)
        return success()
    }
}

class LoggingStorageEvent(message: String, listStore: MutableList<String>) :
    StorageEvent(message, listStore), LoggingEvent

class TestException(message: String) : Exception(message)

class LoggingExceptionCommand : Command(), LoggingCommand

class ExceptionCommandHandler : CommandHandler<Command, Unit, FailureReason>() {
    override suspend fun handle(message: Command): BusResult<Unit, FailureReason> {
        throw TestException("Exception raised")
    }
}

class LoggingExceptionEvent : Event(), LoggingEvent

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

        assertContains(allLogs, "Executing command")
        assertContains(allLogs, "executed command")
    }

    @Test
    fun test_queries_log_with_correct_verbs() = runTest {
        val captureLogger = CaptureLogger()
        val logger = MessageLogger(captureLogger, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR)

        logger.handle(LoggingLogQuery("Testing", captureLogger)) {
            LoggingLogQueryHandler().handle(it)
        }

        val allLogs = captureLogger.logs.joinToString(" | ")

        assertContains(allLogs, "Processing query")
        assertContains(allLogs, "processed query")
    }

    @Test
    fun test_commands_log_exception_and_rethrow() = runTest {
        val captureLogger = CaptureLogger()
        val logger = MessageLogger(captureLogger, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR)

        assertFailsWith<Exception> {
            logger.handle(LoggingExceptionCommand()) { ExceptionCommandHandler().handle(it) }
        }

        val allLogs = captureLogger.logs.joinToString(" | ")

        assertContains(allLogs, "ERROR: Failed executing")
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

        assertContains(allLogs, "Dispatching event")
        assertContains(allLogs, "dispatched event")
    }

    @Test
    fun test_events_log_exception_and_rethrow() = runTest {
        val captureLogger = CaptureLogger()
        val logger = MessageLogger(captureLogger, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR)

        assertFailsWith<Exception> {
            logger.handle(LoggingExceptionEvent()) { ExceptionEventHandler().handle(it) }
        }

        val allLogs = captureLogger.logs.joinToString(" | ")

        assertContains(allLogs, "ERROR: Failed dispatching")
    }
}
