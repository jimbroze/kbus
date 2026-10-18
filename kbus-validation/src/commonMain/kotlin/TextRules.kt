package com.jimbroze.kbus.validation

class NotBlank<out E>(override val failsWith: E) : ValidationRule<String, E> {
    override fun isSatisfiedBy(value: String) = value.isNotBlank()
}

class MinLength<out E>(val minLength: Int, override val failsWith: E) : ValidationRule<String, E> {
    constructor(
        minLength: Int,
        failsWith: (minLength: Int) -> E,
    ) : this(minLength, failsWith(minLength))

    override fun isSatisfiedBy(value: String) = value.length >= minLength
}

class MaxLength<out E>(val maxLength: Int, override val failsWith: E) : ValidationRule<String, E> {
    constructor(
        maxLength: Int,
        failsWith: (maxLength: Int) -> E,
    ) : this(maxLength, failsWith(maxLength))

    override fun isSatisfiedBy(value: String) = value.length <= maxLength
}

/** Satisfied only when [pattern] matches the whole value, not just part of it. */
class Matches<out E>(val pattern: String, override val failsWith: E) : ValidationRule<String, E> {
    private val regex = Regex(pattern)

    override fun isSatisfiedBy(value: String) = regex.matches(value)
}
