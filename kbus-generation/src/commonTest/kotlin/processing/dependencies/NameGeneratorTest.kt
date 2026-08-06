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
    fun `lowercases the first character of a simple class name`() {
        val type = ClassName("com.example", "Foo")

        val result = NameGenerator.getNameForType(type)

        assertEquals("foo", result)
    }

    @Test
    fun `uppercases the first character of a nested name`() {
        val type = ClassName("com.example", "Foo")

        val result = NameGenerator.getNameForType(type, isNested = true)

        assertEquals("Foo", result)
    }

    @Test
    fun `includes the type arguments of a parameterized type`() {
        val type =
            ClassName("kotlin.collections", "List").parameterizedBy(ClassName("kotlin", "String"))

        val result = NameGenerator.getNameForType(type)

        assertEquals("listOfString", result)
    }

    @Test
    fun `joins several type arguments with And`() {
        val type =
            ClassName("kotlin.collections", "Map")
                .parameterizedBy(ClassName("kotlin", "String"), ClassName("kotlin", "Int"))

        val result = NameGenerator.getNameForType(type)

        assertEquals("mapOfStringAndInt", result)
    }

    @Test
    fun `names a lambda type Function`() {
        val type = LambdaTypeName.get(returnType = UNIT)

        val result = NameGenerator.getNameForType(type)

        assertEquals("function", result)
    }

    @Test
    fun `unwraps an out-projected type to the type inside it`() {
        val inner = ClassName("com.example", "Foo")
        val type = WildcardTypeName.producerOf(inner)

        val result = NameGenerator.getNameForType(type)

        assertEquals("foo", result)
    }

    @Test
    fun `unwraps an in-projected type to its out bound`() {
        // consumerOf creates `in Foo` which has an implicit `out Any` bound
        val inner = ClassName("com.example", "Foo")
        val type = WildcardTypeName.consumerOf(inner)

        val result = NameGenerator.getNameForType(type)

        assertEquals("any", result)
    }

    @Test
    fun `names a dynamic type Any`() {
        val result = NameGenerator.getNameForType(Dynamic)

        assertEquals("any", result)
    }

    @Test
    fun `unwraps a star projection to Any`() {
        val result = NameGenerator.getNameForType(STAR)

        assertEquals("any", result)
    }
}
