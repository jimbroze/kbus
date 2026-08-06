package com.jimbroze.kbus.core.middleware

import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.fixtures.CaptureLogger
import com.jimbroze.kbus.core.fixtures.LogLevels
import com.jimbroze.kbus.core.fixtures.LoggingLogCommand
import com.jimbroze.kbus.core.fixtures.LoggingLogCommandHandler
import com.jimbroze.kbus.core.fixtures.OrderCaptureLogger
import com.jimbroze.kbus.core.middleware.middleware.LoggingMiddleware
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MiddlewarePipelineTest {

    @Test
    fun `logs a command that it also passes to its handler`() = runTest {
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
                        LoggingMiddleware(
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
    fun `enters middleware in declaration order and leaves it in reverse`() = runTest {
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
                        LoggingMiddleware(
                            logger1,
                            LogLevels.DEBUG,
                            LogLevels.INFO,
                            LogLevels.ERROR,
                        ),
                        LoggingMiddleware(logger2, LogLevels.DEBUG, LogLevels.INFO, LogLevels.ERROR),
                    ),
            )

        bus.execute(LoggingLogCommand("Test the bus", CaptureLogger()))

        assertTrue(logger1.logs[0] < logger2.logs[0])
        assertTrue(logger2.logs[0] < logger1.logs[1])
        assertTrue(logger2.logs[1] < logger1.logs[1])
    }
}
