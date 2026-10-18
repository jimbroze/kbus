package com.jimbroze.kbus.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class Uppercase<out E>(override val failsWith: E) : ValidationRule<String, E> {
    override fun isSatisfiedBy(value: String) = value == value.uppercase()
}

class UserDefinedRuleTest {
    @Test
    fun `collects the error of a user-defined rule class with the errors of built-in rules`() {
        val rules =
            listOf(
                NotBlank(failsWith = CustomerIdError.Blank),
                Uppercase(failsWith = CustomerIdError.NotUppercase),
                MinLength(3, failsWith = CustomerIdError::TooShort),
            )

        val result = validationResultFor("ab", rules)

        assertIs<ValidationResult.Invalid<CustomerIdError>>(result)
        assertEquals(
            listOf(CustomerIdError.NotUppercase, CustomerIdError.TooShort(minLength = 3)),
            result.errors,
        )
    }

    @Test
    fun `returns the value as valid when a user-defined rule class and built-in rules are all satisfied`() {
        val rules =
            listOf(
                NotBlank(failsWith = CustomerIdError.Blank),
                Uppercase(failsWith = CustomerIdError.NotUppercase),
            )

        val result = validationResultFor("ABC", rules)

        assertIs<ValidationResult.Valid<String>>(result)
    }
}
