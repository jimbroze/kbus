package com.jimbroze.kbus.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class MiddlewareTest {

    @Test
    fun test_MessageLogger_logs_and_executes_command() = runTest {
        val captureLogger = CaptureLogger()
        val bus = MessageBus(listOf(MessageLogger(captureLogger, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR)))

        bus.execute(LoggingLogCommand("Test the bus", CaptureLogger()), LoggingLogCommandHandler())

        assertEquals(2, captureLogger.logs.size)
    }

    @Test
    fun test_MessageBus_handlers_middleware_in_the_correct_order() = runTest {
        val logger1 = TimeCaptureLogger()
        val logger2 = TimeCaptureLogger()
        val bus =
            MessageBus(
                listOf(
                    MessageLogger(logger1, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR),
                    MessageLogger(logger2, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR),
                ),
            )

        bus.execute(LoggingLogCommand("Test the bus", CaptureLogger()), LoggingLogCommandHandler())

        assertTrue(logger1.logs[0] < logger2.logs[0])
        assertTrue(logger2.logs[0] < logger1.logs[1])
        // Order of middleware is reversed for post-handle actions
        assertTrue(logger2.logs[1] < logger1.logs[1])
    }
}
