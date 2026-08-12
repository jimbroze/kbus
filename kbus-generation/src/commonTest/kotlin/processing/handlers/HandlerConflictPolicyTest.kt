package com.jimbroze.kbus.generation.processing.handlers

import com.jimbroze.kbus.generation.processing.ConflictPolicy
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.UNIT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HandlerConflictPolicyTest {

    private fun createHandlerDefinition(
        handlerName: String,
        messageName: String,
    ): HandlerDefinition {
        val handlerData =
            HandlerData(
                handlerClass = ClassName("com.example", handlerName),
                messageClass = ClassName("com.example", messageName),
                returnType = UNIT,
                topLevelDependencies = emptyList(),
            )
        return CommandHandlerDefinition(handlerData)
    }

    @Test
    fun `accepts a handler for a message nothing is registered for`() {
        val handler = createHandlerDefinition("MyHandler", "MyCommand")

        val result = HandlerConflictPolicy.evaluate(handler, emptyList())

        assertIs<ConflictPolicy.Result.Accept>(result)
    }

    @Test
    fun `reports the same handler for the same message as an exact duplicate`() {
        val handler = createHandlerDefinition("MyHandler", "MyCommand")
        val existing = createHandlerDefinition("MyHandler", "MyCommand")

        val result = HandlerConflictPolicy.evaluate(handler, listOf(existing))

        assertIs<ConflictPolicy.Result.ExactDuplicate>(result)
    }

    @Test
    fun `reports a second handler for the same command as a conflict`() {
        val handler = createHandlerDefinition("NewHandler", "MyCommand")
        val existing = createHandlerDefinition("ExistingHandler", "MyCommand")

        val result = HandlerConflictPolicy.evaluate(handler, listOf(existing))

        assertIs<ConflictPolicy.Result.InvalidConflict>(result)
        assertEquals(
            "Message class MyCommand is used by multiple handlers: 'ExistingHandler' & 'NewHandler'",
            result.reason,
        )
    }

    @Test
    fun `accepts a handler for a message of its own`() {
        val handler = createHandlerDefinition("HandlerA", "CommandA")
        val existing = createHandlerDefinition("HandlerB", "CommandB")

        val result = HandlerConflictPolicy.evaluate(handler, listOf(existing))

        assertIs<ConflictPolicy.Result.Accept>(result)
    }

    private fun createEventHandlerDefinition(
        handlerName: String,
        messageName: String,
    ): HandlerDefinition {
        val handlerData =
            HandlerData(
                handlerClass = ClassName("com.example", handlerName),
                messageClass = ClassName("com.example", messageName),
                returnType = UNIT,
                topLevelDependencies = emptyList(),
            )
        return EventHandlerDefinition(handlerData, EventHandlerKind.DOMAIN)
    }

    @Test
    fun `accepts several event handlers for one event`() {
        val handler = createEventHandlerDefinition("HandlerA", "MyEvent")
        val existing = createEventHandlerDefinition("HandlerB", "MyEvent")

        val result = HandlerConflictPolicy.evaluate(handler, listOf(existing))

        assertIs<ConflictPolicy.Result.Accept>(result)
    }

    @Test
    fun `reports the same event handler registered twice as an exact duplicate`() {
        val handler = createEventHandlerDefinition("HandlerA", "MyEvent")
        val existing = createEventHandlerDefinition("HandlerA", "MyEvent")

        val result = HandlerConflictPolicy.evaluate(handler, listOf(existing))

        assertIs<ConflictPolicy.Result.ExactDuplicate>(result)
    }
}
