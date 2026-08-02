package com.jimbroze.kbus.generation.processors.context

import com.jimbroze.kbus.generation.processing.handlers.CommandHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.HandlerData
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.UNIT
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A module learns its dependencies' handlers from their `@KbusIndex` metadata, but must not
 * re-export them as its own — an index says what a module declares, not what it can see.
 */
class ProcessingContextOriginTest {
    private fun commandHandler(commandName: String, module: String = "orders") =
        CommandHandlerDefinition(
            HandlerData(
                ClassName("com.example", "${commandName}Handler"),
                ClassName("com.example", commandName),
                UNIT,
                emptyList(),
                module,
            )
        )

    @Test
    fun aLocallyDeclaredHandlerIsBothVisibleAndLocallyDeclared() {
        val context = ProcessingContext()
        val handler = commandHandler("PlaceOrder")

        context.tryAddHandler(handler)

        assertEquals(setOf(handler), context.handlers)
        assertEquals(setOf(handler), context.locallyDeclaredHandlers)
    }

    @Test
    fun anIndexLearnedHandlerIsVisibleButNotLocallyDeclared() {
        val context = ProcessingContext()
        val handler = commandHandler("PlaceOrder")

        context.tryAddHandler(handler, learnedFromIndex = true)

        assertEquals(setOf(handler), context.handlers)
        assertEquals(emptySet(), context.locallyDeclaredHandlers)
    }

    @Test
    fun aHandlerLearnedFromAnIndexAndThenDeclaredLocallyIsLocallyDeclared() {
        val context = ProcessingContext()
        val handler = commandHandler("PlaceOrder")
        context.tryAddHandler(handler, learnedFromIndex = true)

        context.tryAddHandler(handler)

        assertEquals(setOf(handler), context.locallyDeclaredHandlers)
    }
}
