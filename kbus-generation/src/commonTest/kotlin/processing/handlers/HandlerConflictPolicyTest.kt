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
    fun new_handler_with_no_existing_returns_accept() {
        val handler = createHandlerDefinition("MyHandler", "MyCommand")

        val result = HandlerConflictPolicy.evaluate(handler, emptyList())

        assertIs<ConflictPolicy.Result.Accept>(result)
    }

    @Test
    fun same_handler_and_message_returns_exact_duplicate() {
        val handler = createHandlerDefinition("MyHandler", "MyCommand")
        val existing = createHandlerDefinition("MyHandler", "MyCommand")

        val result = HandlerConflictPolicy.evaluate(handler, listOf(existing))

        assertIs<ConflictPolicy.Result.ExactDuplicate>(result)
    }

    @Test
    fun different_handler_for_same_message_returns_invalid_conflict() {
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
    fun different_message_returns_accept() {
        val handler = createHandlerDefinition("HandlerA", "CommandA")
        val existing = createHandlerDefinition("HandlerB", "CommandB")

        val result = HandlerConflictPolicy.evaluate(handler, listOf(existing))

        assertIs<ConflictPolicy.Result.Accept>(result)
    }
}
