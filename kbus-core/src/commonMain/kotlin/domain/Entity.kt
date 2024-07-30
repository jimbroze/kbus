package com.jimbroze.kbus.core.domain

interface Identifier {
    override fun equals(other: Any?): Boolean
}

abstract class Entity<T : Entity<T>> : HasInvariants() {
    abstract val id: Identifier

    fun hasSameIdentityAs(other: T): Boolean {
        return id == other.id
    }
}

abstract class AggregateRoot<T : AggregateRoot<T>> : Entity<T>()
