package com.jimbroze.kbus.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidationResultForTest {
    private val customerIdRules: List<ValidationRule<String, CustomerIdError>> =
        listOf(
            NotBlank(failsWith = CustomerIdError.Blank),
            MinLength(3, failsWith = CustomerIdError::TooShort),
            MaxLength(40, failsWith = CustomerIdError::TooLong),
        )

    @Test
    fun `returns the value as valid when every rule is satisfied`() {
        val result = validationResultFor("cus_123", customerIdRules)

        assertIs<ValidationResult.Valid<String>>(result)
        assertEquals("cus_123", result.value)
    }

    @Test
    fun `returns the value as valid when there are no rules`() {
        val result = validationResultFor("", emptyList<ValidationRule<String, CustomerIdError>>())

        assertIs<ValidationResult.Valid<String>>(result)
        assertEquals("", result.value)
    }

    @Test
    fun `returns the error of the one rule that fails`() {
        val result = validationResultFor("a", customerIdRules)

        assertIs<ValidationResult.Invalid<CustomerIdError>>(result)
        assertEquals(
            listOf<CustomerIdError>(CustomerIdError.TooShort(minLength = 3)),
            result.errors,
        )
    }

    @Test
    fun `returns the error of every rule that fails in the order the rules are listed`() {
        val result = validationResultFor("", customerIdRules)

        assertIs<ValidationResult.Invalid<CustomerIdError>>(result)
        assertEquals(
            listOf(CustomerIdError.Blank, CustomerIdError.TooShort(minLength = 3)),
            result.errors,
        )
    }
}
