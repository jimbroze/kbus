// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleValidationRules01

import com.jimbroze.kbus.validation.CustomRule
import com.jimbroze.kbus.validation.MinLength
import com.jimbroze.kbus.validation.NotBlank
import com.jimbroze.kbus.validation.ValidationRule
import com.jimbroze.kbus.validation.validationResultFor

sealed interface CustomerIdError {
    data object Blank : CustomerIdError
    data class TooShort(val minLength: Int) : CustomerIdError
    data object NotUppercase : CustomerIdError
    data object ContainsSpace : CustomerIdError
}

// A reusable rule of your own works like the built-in ones
class Uppercase<out E>(override val failsWith: E) : ValidationRule<String, E> {
    override fun isSatisfiedBy(value: String) = value == value.uppercase()
}

val customerIdRules = listOf(
    NotBlank(failsWith = CustomerIdError.Blank),
    MinLength(3, failsWith = CustomerIdError::TooShort), // the error is built from the rule's value
    Uppercase(failsWith = CustomerIdError.NotUppercase),
    CustomRule(description = "has no spaces", failsWith = CustomerIdError.ContainsSpace) { id: String ->
        ' ' !in id
    },
)

// Invalid, with errors [Blank, TooShort(minLength = 3)]
val result = validationResultFor("", customerIdRules)
