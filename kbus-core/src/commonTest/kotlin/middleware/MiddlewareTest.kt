package com.jimbroze.kbus.core.middleware

import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.middleware.middleware.CaptureLogger
import com.jimbroze.kbus.core.middleware.middleware.LogLevels
import com.jimbroze.kbus.core.middleware.middleware.LoggingLogCommand
import com.jimbroze.kbus.core.middleware.middleware.LoggingLogCommandHandler
import com.jimbroze.kbus.core.middleware.middleware.MessageLogger
import com.jimbroze.kbus.core.middleware.middleware.OrderCaptureLogger
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MiddlewareTest {

    @Test
    fun test_MessageLogger_logs_and_executes_command() = runTest {
        val stores = HandlerFactoryStoreCollection()
        stores.commandStore.registerHandlers(
            LoggingLogCommand::class,
            listOf(
                CommandHandlerFactory(LoggingLogCommandHandler::class) {
                    LoggingLogCommandHandler()
                }
            ),
        )

        val captureLogger = CaptureLogger()
        val bus =
            MessageBus(
                PersistingHandlerLocator(stores),
                middlewares =
                    listOf(
                        MessageLogger(
                            captureLogger,
                            LogLevels.DEBUG,
                            LogLevels.INFO,
                            LogLevels.ERROR,
                        )
                    ),
            )

        bus.execute(LoggingLogCommand("Test the bus", CaptureLogger()))

        assertEquals(2, captureLogger.logs.size)
    }

    @Test
    fun test_MessageBus_handlers_middleware_in_the_correct_order() = runTest {
        val stores = HandlerFactoryStoreCollection()
        stores.commandStore.registerHandlers(
            LoggingLogCommand::class,
            listOf(
                CommandHandlerFactory(LoggingLogCommandHandler::class) {
                    LoggingLogCommandHandler()
                }
            ),
        )

        OrderCaptureLogger.orderCounter = 0
        val logger1 = OrderCaptureLogger()
        val logger2 = OrderCaptureLogger()
        val bus =
            MessageBus(
                PersistingHandlerLocator(stores),
                middlewares =
                    listOf(
                        MessageLogger(logger1, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR),
                        MessageLogger(logger2, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR),
                    ),
            )

        bus.execute(LoggingLogCommand("Test the bus", CaptureLogger()))

        assertTrue(logger1.logs[0] < logger2.logs[0])
        assertTrue(logger2.logs[0] < logger1.logs[1])
        // Order of middleware is reversed for post-handle actions
        assertTrue(logger2.logs[1] < logger1.logs[1])
    }
}
