package com.jimbroze.kbus.generation.generators

import com.jimbroze.kbus.generation.processing.handlers.CommandHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerKind
import com.jimbroze.kbus.generation.processing.handlers.HandlerData
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.UNIT
import kotlin.test.Test
import kotlin.test.assertEquals

class BusGeneratorTest {
    private val event = ClassName("com.example", "OrderPlaced")
    private val command = ClassName("com.example", "PlaceOrder")

    private fun commandHandler(handlerName: String, module: String) =
        CommandHandlerDefinition(
            HandlerData(ClassName("com.example", handlerName), command, UNIT, emptyList(), module)
        )

    private fun integrationHandler(handlerName: String, module: String) =
        EventHandlerDefinition(
            HandlerData(ClassName("com.example", handlerName), event, UNIT, emptyList(), module),
            EventHandlerKind.INTEGRATION,
        )

    @Test
    fun contextIdentities_alwaysIncludesDefault() {
        assertEquals(listOf("default"), contextIdentities(emptySet()))
    }

    @Test
    fun contextIdentities_includesAModuleThatOnlyHasACommandHandler() {
        val handlers = setOf(commandHandler("PlaceOrderHandler", "orders"))

        assertEquals(listOf("default", "orders"), contextIdentities(handlers))
    }

    @Test
    fun contextIdentities_dedupesAndSortsModulesAcrossHandlerKinds() {
        val handlers =
            setOf(
                commandHandler("PlaceOrderHandler", "orders"),
                integrationHandler("OrderPlacedHandler", "orders"),
                integrationHandler("StockReservedHandler", "inventory"),
            )

        assertEquals(listOf("default", "inventory", "orders"), contextIdentities(handlers))
    }

    @Test
    fun contextIdentities_omitsTheUnassignedModuleAsItsOwnEntry() {
        val handlers = setOf(commandHandler("PlaceOrderHandler", ""))

        assertEquals(listOf("default"), contextIdentities(handlers))
    }
}
