package com.jimbroze.kbus.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MinLengthTest {
    private val minLengthThree = MinLength(3, failsWith = CustomerIdError::TooShort)

    @Test
    fun `is satisfied by a value longer than the minimum length`() {
        assertTrue(minLengthThree.isSatisfiedBy("abcd"))
    }

    @Test
    fun `is satisfied by a value exactly the minimum length`() {
        assertTrue(minLengthThree.isSatisfiedBy("abc"))
    }

    @Test
    fun `is not satisfied by a value one character shorter than the minimum length`() {
        assertFalse(minLengthThree.isSatisfiedBy("ab"))
    }

    @Test
    fun `counts whitespace towards the length`() {
        assertTrue(minLengthThree.isSatisfiedBy("   "))
    }

    @Test
    fun `fails with the error the given function builds from the minimum length`() {
        assertEquals(CustomerIdError.TooShort(minLength = 3), minLengthThree.failsWith)
    }

    @Test
    fun `fails with the error it was given`() {
        val rule = MinLength(3, failsWith = CustomerIdError.WrongLength)

        assertEquals(CustomerIdError.WrongLength, rule.failsWith)
    }
}
