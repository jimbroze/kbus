package com.jimbroze.kbus.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotBlankTest {
    private val notBlank = NotBlank(failsWith = CustomerIdError.Blank)

    @Test
    fun `is satisfied by a single character`() {
        assertTrue(notBlank.isSatisfiedBy("a"))
    }

    @Test
    fun `is satisfied by text surrounded by whitespace`() {
        assertTrue(notBlank.isSatisfiedBy("  a  "))
    }

    @Test
    fun `is not satisfied by an empty string`() {
        assertFalse(notBlank.isSatisfiedBy(""))
    }

    @Test
    fun `is not satisfied by whitespace only`() {
        assertFalse(notBlank.isSatisfiedBy(" \t\n"))
    }

    @Test
    fun `fails with the error it was given`() {
        assertEquals(CustomerIdError.Blank, notBlank.failsWith)
    }
}
