package com.jimbroze.kbus.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LessThanTest {
    private val lessThanHundred = LessThan(100, failsWith = DiscountPercentError::NotBelow)

    @Test
    fun `is satisfied by a value one below the bound`() {
        assertTrue(lessThanHundred.isSatisfiedBy(99))
    }

    @Test
    fun `is not satisfied by a value equal to the bound`() {
        assertFalse(lessThanHundred.isSatisfiedBy(100))
    }

    @Test
    fun `is not satisfied by a value above the bound`() {
        assertFalse(lessThanHundred.isSatisfiedBy(101))
    }

    @Test
    fun `fails with the error the given function builds from the bound`() {
        assertEquals(
            DiscountPercentError.NotBelow(exclusiveMaximum = 100),
            lessThanHundred.failsWith,
        )
    }

    @Test
    fun `fails with the error it was given`() {
        val rule = LessThan(100, failsWith = DiscountPercentError.WholePrice)

        assertEquals(DiscountPercentError.WholePrice, rule.failsWith)
    }
}
