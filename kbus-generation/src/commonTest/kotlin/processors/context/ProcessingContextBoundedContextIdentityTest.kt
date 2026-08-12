package com.jimbroze.kbus.generation.processors.context

import com.jimbroze.kbus.generation.processing.ConflictPolicy
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerKind
import com.jimbroze.kbus.generation.processing.handlers.HandlerData
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.UNIT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProcessingContextBoundedContextIdentityTest {
    private val event = ClassName("com.example", "OrderPlaced")

    private fun integrationHandler(handlerName: String, module: String) =
        EventHandlerDefinition(
            HandlerData(ClassName("com.example", handlerName), event, UNIT, emptyList(), module),
            EventHandlerKind.INTEGRATION,
        )

    @Test
    fun `keeps handlers from different modules for the same event class`() {
        val context = ProcessingContext()

        val first = context.tryAddHandler(integrationHandler("OrdersHandler", "orders"))
        val second = context.tryAddHandler(integrationHandler("InventoryHandler", "inventory"))

        assertIs<ConflictPolicy.Result.Accept>(first)
        assertIs<ConflictPolicy.Result.Accept>(second)
        assertEquals(
            setOf("orders", "inventory"),
            context.handlers.map { it.handlerData.module }.toSet(),
        )
    }

    @Test
    fun `detects a conflict regardless of the identities the handlers carry`() {
        val context = ProcessingContext()
        context.tryAddHandler(integrationHandler("OrdersHandler", "orders"))

        // Same handler class, different bounded context identity — still the same handler.
        val result = context.tryAddHandler(integrationHandler("OrdersHandler", "inventory"))

        assertIs<ConflictPolicy.Result.ExactDuplicate>(result)
        assertEquals(1, context.handlers.size)
    }

    @Test
    fun `records an unassigned bounded context identity as the empty string`() {
        val handlerData =
            HandlerData(ClassName("com.example", "RootHandler"), event, UNIT, emptyList())

        assertEquals("", handlerData.module)
    }
}
