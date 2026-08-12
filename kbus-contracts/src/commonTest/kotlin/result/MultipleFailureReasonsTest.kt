package com.jimbroze.kbus.contracts.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultipleFailureReasonsTest {

    private class TestFailureReason(override val message: String) : FailureReason

    @Test
    fun `summarises that it holds more than one reason in its message`() {
        val reasons = listOf(TestFailureReason("error 1"), TestFailureReason("error 2"))

        assertTrue(MultipleFailureReasons(reasons).message.contains("multiple failures"))
    }

    @Test
    fun `retains the reasons it was given in order`() {
        val reasons = listOf(TestFailureReason("error 1"), TestFailureReason("error 2"))

        val messages = MultipleFailureReasons(reasons).reasons.map { it.message }

        assertEquals(listOf("error 1", "error 2"), messages)
    }
}
