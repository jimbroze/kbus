package com.jimbroze.kbus.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtMostTest {
    private val atMostTen = AtMost(10, failsWith = QuantityError::TooMany)

    @Test
    fun `is satisfied by a value below the maximum`() {
        assertTrue(atMostTen.isSatisfiedBy(9))
    }

    @Test
    fun `is satisfied by a value equal to the maximum`() {
        assertTrue(atMostTen.isSatisfiedBy(10))
    }

    @Test
    fun `is not satisfied by a value one above the maximum`() {
        assertFalse(atMostTen.isSatisfiedBy(11))
    }

    @Test
    fun `fails with the error the given function builds from the maximum`() {
        assertEquals(QuantityError.TooMany(maximum = 10), atMostTen.failsWith)
    }

    @Test
    fun `fails with the error it was given`() {
        val rule = AtMost(10, failsWith = QuantityError.OverOrderLimit)

        assertEquals(QuantityError.OverOrderLimit, rule.failsWith)
    }
}
