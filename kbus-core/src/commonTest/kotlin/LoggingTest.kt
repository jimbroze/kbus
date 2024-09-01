package com.jimbroze.kbus.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.TimeSource


class CaptureLogger : Logger {
    val logs = mutableListOf<String>()
    override fun info(message: String) {
        logs.add("info: $message")
    }

    override fun error(message: String) {
        logs.add("error: $message")
    }
}
class TimeCaptureLogger : Logger {
    private val timeSource = TimeSource.Monotonic
    val logs = mutableListOf<TimeSource.Monotonic.ValueTimeMark>()
    override fun info(message: String) {
        logs.add(timeSource.markNow())
    }

    override fun error(message: String) {
        logs.add(timeSource.markNow())
    }
}

class LoggingLogCommand(val messageToLog: String, val logger: Logger) : Command(), LoggingCommand

class LoggingLogCommandHandler : CommandHandler<LoggingLogCommand, Unit, FailureReason> {
    override suspend fun handle(message: LoggingLogCommand): BusResult<Unit, FailureReason> {
        message.logger.info(message.messageToLog)
        return success()
    }
}

class LoggingStorageEvent(message: String, listStore: MutableList<String>) : StorageEvent(message, listStore), LoggingEvent

class LoggingExceptionCommand : Command(), LoggingCommand

class ExceptionCommandHandler : CommandHandler<Command, Unit, FailureReason> {
    override suspend fun handle(message: Command): BusResult<Unit, FailureReason> {
        throw Exception("Exception raised")
    }
}

class LoggingExceptionEvent : Event(), LoggingEvent

class ExceptionEventHandler : EventHandler<Event> {
    override suspend fun handle(message: Event) {
        throw Exception("Exception raised")
    }
}

class LoggingTest {

    @Test
    fun message_logger_does_not_log_messages_that_do_not_implement_logging_interface() = runTest {
        val captureLogger = CaptureLogger()
        val logger = MessageLogger(captureLogger)

        logger.handle(StorageCommand("Testing", mutableListOf())) { StorageCommandHandler().handle(it) }

        assertEquals(0, captureLogger.logs.size)
    }

    @Test
    fun message_logger_logs_before_and_after_message_using_info() = runTest {
        val captureLogger = CaptureLogger()
        val logger = MessageLogger(captureLogger)

        logger.handle(LoggingLogCommand("Testing", captureLogger)) { LoggingLogCommandHandler().handle(it) }

        assertEquals(3, captureLogger.logs.size)
        assertContains(captureLogger.logs[0], "info: ")
        assertEquals("info: Testing", captureLogger.logs[1])
        assertContains(captureLogger.logs[2], "info: Successfully")
    }

    @Test
    fun test_commands_log_with_correct_verbs() = runTest {
        val captureLogger = CaptureLogger()
        val logger = MessageLogger(captureLogger)

        logger.handle(LoggingLogCommand("Testing", captureLogger)) { LoggingLogCommandHandler().handle(it) }

        val allLogs = captureLogger.logs.joinToString(" | ")

        assertContains(allLogs, "Executing command")
        assertContains(allLogs, "executed command")
    }

    @Test
    fun test_commands_log_exception_and_rethrow() = runTest {
        val captureLogger = CaptureLogger()
        val logger = MessageLogger(captureLogger)

        assertFailsWith<Exception> {
            logger.handle(LoggingExceptionCommand()) { ExceptionCommandHandler().handle(it) }
        }

        val allLogs = captureLogger.logs.joinToString(" | ")

        assertContains(allLogs, "error: Failed executing")
    }

    @Test
    fun test_events_log_with_correct_verbs() = runTest {
        val captureLogger = CaptureLogger()
        val logger = MessageLogger(captureLogger)

        logger.handle(LoggingStorageEvent("Testing", mutableListOf())) { PrintEventHandler().handle(it) }

        val allLogs = captureLogger.logs.joinToString(" | ")

        assertContains(allLogs, "Dispatching event")
        assertContains(allLogs, "dispatched event")
    }

    @Test
    fun test_events_log_exception_and_rethrow() = runTest {
        val captureLogger = CaptureLogger()
        val logger = MessageLogger(captureLogger)

        assertFailsWith<Exception> {
            logger.handle(LoggingExceptionEvent()) { ExceptionEventHandler().handle(it) }
        }

        val allLogs = captureLogger.logs.joinToString(" | ")

        assertContains(allLogs, "error: Failed dispatching")
    }
}
