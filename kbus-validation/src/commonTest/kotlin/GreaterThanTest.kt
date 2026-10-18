package com.jimbroze.kbus.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GreaterThanTest {
    private val greaterThanZero = GreaterThan(0, failsWith = PriceInPenceError::NotAbove)

    @Test
    fun `is satisfied by a value one above the bound`() {
        assertTrue(greaterThanZero.isSatisfiedBy(1))
    }

    @Test
    fun `is not satisfied by a value equal to the bound`() {
        assertFalse(greaterThanZero.isSatisfiedBy(0))
    }

    @Test
    fun `is not satisfied by a value below the bound`() {
        assertFalse(greaterThanZero.isSatisfiedBy(-1))
    }

    @Test
    fun `fails with the error the given function builds from the bound`() {
        assertEquals(PriceInPenceError.NotAbove(exclusiveMinimum = 0), greaterThanZero.failsWith)
    }

    @Test
    fun `fails with the error it was given`() {
        val rule = GreaterThan(0, failsWith = PriceInPenceError.Free)

        assertEquals(PriceInPenceError.Free, rule.failsWith)
    }
}
