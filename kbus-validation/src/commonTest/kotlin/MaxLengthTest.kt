package com.jimbroze.kbus.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaxLengthTest {
    private val maxLengthFive = MaxLength(5, failsWith = CustomerIdError::TooLong)

    @Test
    fun `is satisfied by a value shorter than the maximum length`() {
        assertTrue(maxLengthFive.isSatisfiedBy("abcd"))
    }

    @Test
    fun `is satisfied by a value exactly the maximum length`() {
        assertTrue(maxLengthFive.isSatisfiedBy("abcde"))
    }

    @Test
    fun `is not satisfied by a value one character longer than the maximum length`() {
        assertFalse(maxLengthFive.isSatisfiedBy("abcdef"))
    }

    @Test
    fun `fails with the error the given function builds from the maximum length`() {
        assertEquals(CustomerIdError.TooLong(maxLength = 5), maxLengthFive.failsWith)
    }

    @Test
    fun `fails with the error it was given`() {
        val rule = MaxLength(5, failsWith = CustomerIdError.WrongLength)

        assertEquals(CustomerIdError.WrongLength, rule.failsWith)
    }
}
