package com.jimbroze.kbus.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class ValueObjectTest {
    @Test
    fun test_ValueObject_with_same_values_match() {
        val valueObject1 = TestValueObject("howdy")
        val valueObject2 = TestValueObject("howdy")

        val sameValue = valueObject1.hasSameValueAs(valueObject2)

        assertTrue(sameValue)
        assertEquals(valueObject1, valueObject2)
        assertNotSame(valueObject1, valueObject2)
    }

    @Test
    fun test_ValueObject_with_different_values_do_not_match() {
        val valueObject1 = TestValueObject("howdy")
        val valueObject2 = TestValueObject("aye up")

        val sameValue = valueObject1.hasSameValueAs(valueObject2)

        assertFalse(sameValue)
        assertNotEquals(valueObject1, valueObject2)
        assertNotSame(valueObject1, valueObject2)
    }

    @Test
    fun test_false_assertion_throws_invalid_invariant_exception() {
        assertFailsWith<CannotBeEmptyException>("Value Object cannot be empty") {
            TestNonEmptyValueObject("")
        }
        assertFailsWith<InvalidInvariantException>("Value Object cannot be empty") {
            TestNonEmptyValueObject("")
        }
    }
}
