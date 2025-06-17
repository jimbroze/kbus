package com.jimbroze.kbus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class EventDispatcherTest {
    @Test
    fun test_it_dispatches_event_to_all_handlers() = runTest {
        val results = mutableListOf<String>()
        val dispatcher = EventDispatcher(emptyList())

        dispatcher.dispatch(
            StorageEvent("string", results),
            listOf(PrintEventHandler(), OtherPrintEventHandler("other string")),
        )

        assertEquals("string", results[0])
        assertEquals("other string", results[1])
    }
}
