package com.jimbroze.kbus.domain

class TestIdentifier(val intId: Int) : Identifier {
    override fun equals(other: Any?): Boolean {
        return other is TestIdentifier && intId == other.intId
    }

    override fun hashCode(): Int {
        return intId
    }
}

class TestEntity(override val id: TestIdentifier) : Entity<TestEntity>()

open class TestValueObject(val data: String) : ValueObject<TestValueObject>() {
    override fun equals(other: Any?): Boolean {
        return other is TestValueObject && data == other.data
    }

    override fun hashCode(): Int {
        return data.hashCode()
    }
}
