package com.jimbroze.kbus.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ValueObjectTest {
    @Test
    fun `has the same value as a distinct instance holding the same contents`() {
        val valueObject = TestValueObject("howdy")

        assertTrue(valueObject.hasSameValueAs(TestValueObject("howdy")))
    }

    @Test
    fun `does not have the same value as an instance holding different contents`() {
        val valueObject = TestValueObject("howdy")

        assertFalse(valueObject.hasSameValueAs(TestValueObject("aye up")))
    }

    @Test
    fun `equals a distinct instance holding the same contents`() {
        assertEquals(TestValueObject("howdy"), TestValueObject("howdy"))
    }

    @Test
    fun `does not equal an instance holding different contents`() {
        assertNotEquals(TestValueObject("howdy"), TestValueObject("aye up"))
    }
}
