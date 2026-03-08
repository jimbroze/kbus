package com.jimbroze.kbus.contracts.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BusResultTest {

    private class TestFailureReason(override val message: String) : FailureReason

    private class TestMessageFailure(override val reason: FailureReason) : MessageFailure

    @Test
    fun success_isSuccess_returns_true() {
        val result = BusResult.success("value")

        assertTrue(result.isSuccess)
    }

    @Test
    fun success_isFailure_returns_false() {
        val result = BusResult.success("value")

        assertFalse(result.isFailure)
    }

    @Test
    fun success_getOrNull_returns_value() {
        val result = BusResult.success("hello")

        assertEquals("hello", result.getOrNull())
    }

    @Test
    fun success_failureOrNull_returns_null() {
        val result = BusResult.success("value")

        assertNull(result.failureOrNull())
    }

    @Test
    fun failure_isSuccess_returns_false() {
        val failure = TestMessageFailure(TestFailureReason("error"))
        val result = BusResult.failure(failure)

        assertFalse(result.isSuccess)
    }

    @Test
    fun failure_isFailure_returns_true() {
        val failure = TestMessageFailure(TestFailureReason("error"))
        val result = BusResult.failure(failure)

        assertTrue(result.isFailure)
    }

    @Test
    fun failure_getOrNull_returns_null() {
        val failure = TestMessageFailure(TestFailureReason("error"))
        val result = BusResult.failure(failure)

        assertNull(result.getOrNull())
    }

    @Test
    fun failure_failureOrNull_returns_the_failure() {
        val failure = TestMessageFailure(TestFailureReason("error"))
        val result = BusResult.failure(failure)

        assertEquals(failure, result.failureOrNull())
    }

    @Test
    fun success_toString_contains_value() {
        val result = BusResult.success(42)

        assertEquals("Success(42)", result.toString())
    }

    @Test
    fun failure_toString_contains_message() {
        val failure = TestMessageFailure(TestFailureReason("something went wrong"))
        val result = BusResult.failure(failure)

        assertEquals("Failure: something went wrong", result.toString())
    }

    @Test
    fun multipleFailureReasons_aggregates_messages() {
        val reasons = listOf(TestFailureReason("error 1"), TestFailureReason("error 2"))
        val multi = MultipleFailureReasons(reasons)

        assertTrue(multi.message.contains("multiple failures"))
        assertEquals(2, multi.reasons.size)
        assertEquals("error 1", multi.reasons[0].message)
        assertEquals("error 2", multi.reasons[1].message)
    }

    @Test
    fun success_with_null_value() {
        val result = BusResult.success(null)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }
}
