package com.jimbroze.kbus.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntityTest {
    @Test
    fun `has the same identity as an entity with the same id`() {
        val entity1 = TestEntity(TestIdentifier(1))
        val entity2 = TestEntity(TestIdentifier(1))

        val identical = entity1.hasSameIdentityAs(entity2)

        assertTrue(identical)
    }

    @Test
    fun `does not have the same identity as an entity with a different id`() {
        val entity1 = TestEntity(TestIdentifier(1))
        val entity2 = TestEntity(TestIdentifier(2))

        val identical = entity1.hasSameIdentityAs(entity2)

        assertFalse(identical)
    }
}
