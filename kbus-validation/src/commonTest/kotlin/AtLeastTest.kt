package com.jimbroze.kbus.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtLeastTest {
    private val atLeastOne = AtLeast(1, failsWith = QuantityError::TooFew)

    @Test
    fun `is satisfied by a value above the minimum`() {
        assertTrue(atLeastOne.isSatisfiedBy(2))
    }

    @Test
    fun `is satisfied by a value equal to the minimum`() {
        assertTrue(atLeastOne.isSatisfiedBy(1))
    }

    @Test
    fun `is not satisfied by a value one below the minimum`() {
        assertFalse(atLeastOne.isSatisfiedBy(0))
    }

    @Test
    fun `fails with the error the given function builds from the minimum`() {
        assertEquals(QuantityError.TooFew(minimum = 1), atLeastOne.failsWith)
    }

    @Test
    fun `fails with the error it was given`() {
        val rule = AtLeast(1, failsWith = QuantityError.NoneOrdered)

        assertEquals(QuantityError.NoneOrdered, rule.failsWith)
    }
}
