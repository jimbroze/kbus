package com.jimbroze.kbus.validation

sealed interface ValidationResult<out T, out E> {
    class Valid<out T>(val value: T) : ValidationResult<T, Nothing>

    class Invalid<out E>(val errors: NonEmptyList<E>) : ValidationResult<Nothing, E>
}

/**
 * Checks [value] against every rule in [rules], not stopping at the first failure.
 *
 * @return [ValidationResult.Valid] holding [value] when every rule is satisfied; otherwise
 *   [ValidationResult.Invalid] holding the error of each unsatisfied rule, in the order of [rules].
 */
fun <T, E> validationResultFor(
    value: T,
    rules: List<ValidationRule<T, E>>,
): ValidationResult<T, E> {
    val errors =
        rules.filterNot { rule -> rule.isSatisfiedBy(value) }.map { rule -> rule.failsWith }
    return if (errors.isEmpty()) {
        ValidationResult.Valid(value)
    } else {
        ValidationResult.Invalid(NonEmptyList(errors.first(), errors.drop(1)))
    }
}
