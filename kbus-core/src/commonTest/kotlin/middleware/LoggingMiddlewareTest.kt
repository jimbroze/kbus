package com.jimbroze.kbus.core.middleware

import com.jimbroze.kbus.core.fixtures.CaptureLogger
import com.jimbroze.kbus.core.fixtures.EmptyMiddlewareInvocationContext
import com.jimbroze.kbus.core.fixtures.ExceptionCommandHandler
import com.jimbroze.kbus.core.fixtures.ExceptionEventHandler
import com.jimbroze.kbus.core.fixtures.LogLevels
import com.jimbroze.kbus.core.fixtures.LoggingExceptionCommand
import com.jimbroze.kbus.core.fixtures.LoggingExceptionEvent
import com.jimbroze.kbus.core.fixtures.LoggingLogCommand
import com.jimbroze.kbus.core.fixtures.LoggingLogCommandHandler
import com.jimbroze.kbus.core.fixtures.LoggingLogQuery
import com.jimbroze.kbus.core.fixtures.LoggingLogQueryHandler
import com.jimbroze.kbus.core.fixtures.LoggingStorageEvent
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageCommand
import com.jimbroze.kbus.core.fixtures.StorageCommandHandler
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LoggingMiddlewareTest {

    @Test
    fun `logs nothing for a message that does not opt into logging`() = runTest {
        val captureLogger = CaptureLogger()
        val logger =
            LoggingMiddleware(captureLogger, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR)

        logger.handle(
            StorageCommand("Testing", mutableListOf()),
            EmptyMiddlewareInvocationContext,
        ) {
            StorageCommandHandler().handle(it)
        }

        assertEquals(0, captureLogger.logs.size)
    }

    @Test
    fun `logs before and after a message at the levels it was given`() = runTest {
        val captureLogger = CaptureLogger()
        val logger =
            LoggingMiddleware(captureLogger, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR)

        logger.handle(
            LoggingLogCommand("Testing", captureLogger),
            EmptyMiddlewareInvocationContext,
        ) {
            LoggingLogCommandHandler().handle(it)
        }

        assertEquals(3, captureLogger.logs.size)
        assertContains(captureLogger.logs[0], "DEBUG: ")
        assertEquals("INFO: Testing", captureLogger.logs[1])
        assertContains(captureLogger.logs[2], "INFO: Successfully")
    }

    @Test
    fun `describes a command it handles as a command`() = runTest {
        val captureLogger = CaptureLogger()
        val logger =
            LoggingMiddleware(captureLogger, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR)

        logger.handle(
            LoggingLogCommand("Testing", captureLogger),
            EmptyMiddlewareInvocationContext,
        ) {
            LoggingLogCommandHandler().handle(it)
        }

        val allLogs = captureLogger.logs.joinToString(" | ")

        assertContains(allLogs, "Handling command")
        assertContains(allLogs, "handled command")
    }

    @Test
    fun `describes a query it handles as a query`() = runTest {
        val captureLogger = CaptureLogger()
        val logger =
            LoggingMiddleware(captureLogger, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR)

        logger.handle(LoggingLogQuery("Testing", captureLogger), EmptyMiddlewareInvocationContext) {
            LoggingLogQueryHandler().handle(it)
        }

        val allLogs = captureLogger.logs.joinToString(" | ")

        assertContains(allLogs, "Handling query")
        assertContains(allLogs, "handled query")
    }

    @Test
    fun `logs the failure and rethrows when a command handler throws`() = runTest {
        val captureLogger = CaptureLogger()
        val logger =
            LoggingMiddleware(captureLogger, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR)

        assertFailsWith<Exception> {
            logger.handle(LoggingExceptionCommand(), EmptyMiddlewareInvocationContext) {
                ExceptionCommandHandler().handle(it)
            }
        }

        val allLogs = captureLogger.logs.joinToString(" | ")

        assertContains(allLogs, "ERROR: Failed handling")
        assertTrue(
            captureLogger.exceptions.any { it is Exception && it.message == "Exception raised" }
        )
    }

    @Test
    fun `describes an event it handles as an event`() = runTest {
        val captureLogger = CaptureLogger()
        val logger =
            LoggingMiddleware(captureLogger, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR)

        logger.handle(
            LoggingStorageEvent("Testing", mutableListOf()),
            EmptyMiddlewareInvocationContext,
        ) {
            PrintEventHandler().handle(it)
        }

        val allLogs = captureLogger.logs.joinToString(" | ")

        assertContains(allLogs, "Handling event")
        assertContains(allLogs, "handled event")
    }

    @Test
    fun `logs the failure and rethrows when an event handler throws`() = runTest {
        val captureLogger = CaptureLogger()
        val logger =
            LoggingMiddleware(captureLogger, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR)

        assertFailsWith<Exception> {
            logger.handle(LoggingExceptionEvent(), EmptyMiddlewareInvocationContext) {
                ExceptionEventHandler().handle(it)
            }
        }

        val allLogs = captureLogger.logs.joinToString(" | ")

        assertContains(allLogs, "ERROR: Failed handling")
    }
}
