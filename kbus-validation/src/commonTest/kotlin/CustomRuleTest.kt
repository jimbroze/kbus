package com.jimbroze.kbus.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomRuleTest {
    private class DeliveryWindow(val earliestInDays: Int, val latestInDays: Int)

    private val earliestOnOrBeforeLatest =
        CustomRule<DeliveryWindow, DeliveryWindowError>(
            description = "earliest is on or before latest",
            failsWith = DeliveryWindowError.EarliestAfterLatest,
        ) { window ->
            window.earliestInDays <= window.latestInDays
        }

    @Test
    fun `is satisfied by a value its predicate accepts`() {
        assertTrue(earliestOnOrBeforeLatest.isSatisfiedBy(DeliveryWindow(2, 2)))
    }

    @Test
    fun `is not satisfied by a value its predicate rejects`() {
        assertFalse(earliestOnOrBeforeLatest.isSatisfiedBy(DeliveryWindow(3, 2)))
    }

    @Test
    fun `fails with the error it was given`() {
        assertEquals(DeliveryWindowError.EarliestAfterLatest, earliestOnOrBeforeLatest.failsWith)
    }

    @Test
    fun `exposes the description it was given`() {
        assertEquals("earliest is on or before latest", earliestOnOrBeforeLatest.description)
    }
}
