package com.jimbroze.kbus.core.domain

class TestIdentifier(val intId: Int) : Identifier {
    override fun equals(other: Any?): Boolean {
        return other is TestIdentifier && intId == other.intId
    }

    override fun hashCode(): Int {
        return intId
    }
}

class TestEntity(override val id: TestIdentifier) : Entity<TestEntity>()

class TestEntityWithMaxIdOfFive(override val id: TestIdentifier) : Entity<TestEntity>() {
    init {
        assert(id.intId <= 5, "ID must be no greater than 5")
    }
}

open class TestValueObject(val data: String) : ValueObject<TestValueObject>() {
    override fun equals(other: Any?): Boolean {
        return other is TestValueObject && data == other.data
    }

    override fun hashCode(): Int {
        return data.hashCode()
    }
}

class CannotBeEmptyException : InvalidInvariantException("Value Object cannot be empty")

class TestNonEmptyValueObject(data: String) : TestValueObject(data) {
    init {
        assert(data.isNotEmpty(), CannotBeEmptyException())
    }
}
