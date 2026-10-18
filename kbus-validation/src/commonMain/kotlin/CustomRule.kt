package com.jimbroze.kbus.validation

class CustomRule<in T, out E>(
    val description: String,
    override val failsWith: E,
    private val predicate: (T) -> Boolean,
) : ValidationRule<T, E> {
    override fun isSatisfiedBy(value: T) = predicate(value)
}
