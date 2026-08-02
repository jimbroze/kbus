package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import com.jimbroze.kbus.generation.processing.handlers.CommandHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.HandlerData
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.UNIT
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class RecordingLogger : KSPLogger {
    val errors = mutableListOf<String>()

    override fun logging(message: String, symbol: KSNode?) = Unit

    override fun info(message: String, symbol: KSNode?) = Unit

    override fun warn(message: String, symbol: KSNode?) = Unit

    override fun error(message: String, symbol: KSNode?) {
        errors += message
    }

    override fun exception(e: Throwable) = throw e
}

class ContextIdentityCollisionsTest {
    private val logger = RecordingLogger()

    private fun commandHandler(handlerName: String, module: String) =
        CommandHandlerDefinition(
            HandlerData(
                ClassName("com.example", handlerName),
                ClassName("com.example", "PlaceOrder"),
                UNIT,
                emptyList(),
                module,
            )
        )

    @Test
    fun identitiesDifferingOnlyBySeparatorsAreRejected() {
        val handlers =
            setOf(
                commandHandler("PlaceOrderHandler", "order-fulfilment"),
                commandHandler("ShipOrderHandler", "order_fulfilment"),
            )

        assertFalse(reportContextIdentityCollisions(handlers, logger))
        assertEquals(1, logger.errors.size)
        assertContains(logger.errors.single(), "'order-fulfilment'")
        assertContains(logger.errors.single(), "'order_fulfilment'")
    }

    @Test
    fun aCollisionNamesTheHandlersThatDeclaredEachIdentity() {
        val handlers =
            setOf(
                commandHandler("PlaceOrderHandler", "order.fulfilment"),
                commandHandler("ShipOrderHandler", "orderFulfilment"),
            )

        reportContextIdentityCollisions(handlers, logger)

        assertContains(logger.errors.single(), "com.example.PlaceOrderHandler")
        assertContains(logger.errors.single(), "com.example.ShipOrderHandler")
    }

    @Test
    fun identitiesThatGenerateDistinctNamesAreAccepted() {
        val handlers =
            setOf(
                commandHandler("PlaceOrderHandler", "orders"),
                commandHandler("ReserveStockHandler", "inventory"),
                commandHandler("SendEmailHandler", ""),
            )

        assertTrue(reportContextIdentityCollisions(handlers, logger))
        assertEquals(emptyList(), logger.errors)
    }
}
