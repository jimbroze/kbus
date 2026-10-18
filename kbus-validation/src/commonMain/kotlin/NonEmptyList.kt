package com.jimbroze.kbus.validation

class NonEmptyList<out E>(first: E, rest: List<E>) : AbstractList<E>() {
    private val elements = listOf(first) + rest

    override val size: Int
        get() = elements.size

    override fun get(index: Int): E = elements[index]
}
