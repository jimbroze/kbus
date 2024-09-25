package com.jimbroze.kbus.core.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntityTest {
    @Test
    fun test_Entity_with_same_ids_match() {
        val entity1 = TestEntity(TestIdentifier(1))
        val entity2 = TestEntity(TestIdentifier(1))

        val identical = entity1.hasSameIdentityAs(entity2)

        assertTrue(identical)
    }

    @Test
    fun test_Entity_with_different_ids_do_not_match() {
        val entity1 = TestEntity(TestIdentifier(1))
        val entity2 = TestEntity(TestIdentifier(2))

        val identical = entity1.hasSameIdentityAs(entity2)

        assertFalse(identical)
    }

    @Test
    fun test_false_assertion_throws_invalid_invariant_exception() {
        assertFailsWith<InvalidInvariantException>("ID must be no greater than 5") {
            TestEntityWithMaxIdOfFive(TestIdentifier(6))
        }
    }
}
