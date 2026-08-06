package com.jimbroze.kbus.generation.processing

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TypeResolverTest {

    @Test
    fun `resolves a simple class name`() {
        val result = TypeResolver.resolve("com.example.Foo")

        assertIs<ClassName>(result)
        assertEquals("Foo", (result).simpleName)
        assertEquals("com.example", result.packageName)
    }

    @Test
    fun `resolves a nullable type`() {
        val result = TypeResolver.resolve("com.example.Foo?")

        assertTrue(result.isNullable)
        val nonNull = result.copy(nullable = false)
        assertIs<ClassName>(nonNull)
        assertEquals("Foo", (nonNull).simpleName)
    }

    @Test
    fun `resolves a generic type`() {
        val result = TypeResolver.resolve("com.example.Foo<com.example.Bar>")

        assertIs<ParameterizedTypeName>(result)
        val parameterized = result
        assertEquals("Foo", parameterized.rawType.simpleName)
        assertEquals(1, parameterized.typeArguments.size)
        val arg = parameterized.typeArguments[0]
        assertIs<ClassName>(arg)
        assertEquals("Bar", (arg).simpleName)
    }

    @Test
    fun `resolves generics nested inside generics`() {
        val result =
            TypeResolver.resolve(
                "kotlin.collections.Map<kotlin.String, kotlin.collections.List<kotlin.Int>>"
            )

        assertIs<ParameterizedTypeName>(result)
        val parameterized = result
        assertEquals("Map", parameterized.rawType.simpleName)
        assertEquals(2, parameterized.typeArguments.size)

        val firstArg = parameterized.typeArguments[0]
        assertIs<ClassName>(firstArg)
        assertEquals("String", (firstArg).simpleName)

        val secondArg = parameterized.typeArguments[1]
        assertIs<ParameterizedTypeName>(secondArg)
        assertEquals("List", (secondArg).rawType.simpleName)
    }

    @Test
    fun `resolves a type with several type arguments`() {
        val result = TypeResolver.resolve("kotlin.Pair<kotlin.String, kotlin.Int>")

        assertIs<ParameterizedTypeName>(result)
        assertEquals("Pair", result.rawType.simpleName)
        assertEquals(2, result.typeArguments.size)
        assertEquals("String", (result.typeArguments[0] as ClassName).simpleName)
        assertEquals("Int", (result.typeArguments[1] as ClassName).simpleName)
    }

    @Test
    fun `strips the backticks from an escaped name`() {
        val result = TypeResolver.resolve("`com.example.Foo`")

        assertIs<ClassName>(result)
        assertEquals("Foo", (result).simpleName)
    }

    @Test
    fun `resolves a class name for a type with no arguments`() {
        val result = TypeResolver.resolveClassName("com.example.Foo")

        assertEquals("Foo", result.simpleName)
        assertEquals("com.example", result.packageName)
    }

    @Test
    fun `refuses a class name for a parameterized type`() {
        assertFailsWith<IllegalArgumentException> {
            TypeResolver.resolveClassName("com.example.Foo<com.example.Bar>")
        }
    }
}
