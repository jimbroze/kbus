package com.jimbroze.kbus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class CommandExecutorTest {
    @Test
    fun test_it_invokes_handler_and_returns_result() = runTest {
        val executor = CommandExecutor(emptyList(), TestBusAccess())

        val result = executor.execute(ReturnCommand("Wassup"), ReturnCommandHandler())

        assertEquals(BusResult.success("Wassup"), result)
    }
}

class TestBusAccess : BusAccess {
    override suspend fun <TEvent : Event> dispatch(event: TEvent) {
        // No-op
    }
}
