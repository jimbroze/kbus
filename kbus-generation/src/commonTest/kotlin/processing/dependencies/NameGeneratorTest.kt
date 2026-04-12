package com.jimbroze.kbus.generation.processing.dependencies

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.Dynamic
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.WildcardTypeName
import kotlin.test.Test
import kotlin.test.assertEquals

class NameGeneratorTest {

    @Test
    fun simple_class_name_returns_lowercase_first_char() {
        val type = ClassName("com.example", "Foo")

        val result = NameGenerator.getNameForType(type)

        assertEquals("foo", result)
    }

    @Test
    fun nested_name_returns_uppercase_first_char() {
        val type = ClassName("com.example", "Foo")

        val result = NameGenerator.getNameForType(type, isNested = true)

        assertEquals("Foo", result)
    }

    @Test
    fun parameterized_type_includes_type_args() {
        val type =
            ClassName("kotlin.collections", "List").parameterizedBy(ClassName("kotlin", "String"))

        val result = NameGenerator.getNameForType(type)

        assertEquals("listOfString", result)
    }

    @Test
    fun multiple_type_args_joined_with_and() {
        val type =
            ClassName("kotlin.collections", "Map")
                .parameterizedBy(ClassName("kotlin", "String"), ClassName("kotlin", "Int"))

        val result = NameGenerator.getNameForType(type)

        assertEquals("mapOfStringAndInt", result)
    }

    @Test
    fun lambda_type_returns_function() {
        val type = LambdaTypeName.get(returnType = UNIT)

        val result = NameGenerator.getNameForType(type)

        assertEquals("function", result)
    }

    @Test
    fun wildcard_out_type_unwraps_to_inner() {
        val inner = ClassName("com.example", "Foo")
        val type = WildcardTypeName.producerOf(inner)

        val result = NameGenerator.getNameForType(type)

        assertEquals("foo", result)
    }

    @Test
    fun wildcard_in_type_unwraps_to_out_bound() {
        // consumerOf creates `in Foo` which has an implicit `out Any` bound
        val inner = ClassName("com.example", "Foo")
        val type = WildcardTypeName.consumerOf(inner)

        val result = NameGenerator.getNameForType(type)

        assertEquals("any", result)
    }

    @Test
    fun dynamic_type_returns_any() {
        val result = NameGenerator.getNameForType(Dynamic)

        assertEquals("any", result)
    }

    @Test
    fun star_projection_unwraps_to_any() {
        val result = NameGenerator.getNameForType(STAR)

        assertEquals("any", result)
    }
}
