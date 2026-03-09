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
    fun resolve_simple_class_name() {
        val result = TypeResolver.resolve("com.example.Foo")

        assertIs<ClassName>(result)
        assertEquals("Foo", (result as ClassName).simpleName)
        assertEquals("com.example", result.packageName)
    }

    @Test
    fun resolve_nullable_type() {
        val result = TypeResolver.resolve("com.example.Foo?")

        assertTrue(result.isNullable)
        val nonNull = result.copy(nullable = false)
        assertIs<ClassName>(nonNull)
        assertEquals("Foo", (nonNull as ClassName).simpleName)
    }

    @Test
    fun resolve_generic_type() {
        val result = TypeResolver.resolve("com.example.Foo<com.example.Bar>")

        assertIs<ParameterizedTypeName>(result)
        val parameterized = result as ParameterizedTypeName
        assertEquals("Foo", parameterized.rawType.simpleName)
        assertEquals(1, parameterized.typeArguments.size)
        val arg = parameterized.typeArguments[0]
        assertIs<ClassName>(arg)
        assertEquals("Bar", (arg as ClassName).simpleName)
    }

    @Test
    fun resolve_nested_generics() {
        val result =
            TypeResolver.resolve(
                "kotlin.collections.Map<kotlin.String, kotlin.collections.List<kotlin.Int>>"
            )

        assertIs<ParameterizedTypeName>(result)
        val parameterized = result as ParameterizedTypeName
        assertEquals("Map", parameterized.rawType.simpleName)
        assertEquals(2, parameterized.typeArguments.size)

        val firstArg = parameterized.typeArguments[0]
        assertIs<ClassName>(firstArg)
        assertEquals("String", (firstArg as ClassName).simpleName)

        val secondArg = parameterized.typeArguments[1]
        assertIs<ParameterizedTypeName>(secondArg)
        assertEquals("List", (secondArg as ParameterizedTypeName).rawType.simpleName)
    }

    @Test
    fun resolve_multiple_type_arguments() {
        val result = TypeResolver.resolve("kotlin.Pair<kotlin.String, kotlin.Int>")

        assertIs<ParameterizedTypeName>(result)
        val parameterized = result as ParameterizedTypeName
        assertEquals("Pair", parameterized.rawType.simpleName)
        assertEquals(2, parameterized.typeArguments.size)
        assertEquals("String", (parameterized.typeArguments[0] as ClassName).simpleName)
        assertEquals("Int", (parameterized.typeArguments[1] as ClassName).simpleName)
    }

    @Test
    fun resolve_removes_backticks() {
        val result = TypeResolver.resolve("`com.example.Foo`")

        assertIs<ClassName>(result)
        assertEquals("Foo", (result as ClassName).simpleName)
    }

    @Test
    fun resolveClassName_returns_class_name_for_simple_type() {
        val result = TypeResolver.resolveClassName("com.example.Foo")

        assertEquals("Foo", result.simpleName)
        assertEquals("com.example", result.packageName)
    }

    @Test
    fun resolveClassName_throws_for_parameterized_type() {
        assertFailsWith<IllegalArgumentException> {
            TypeResolver.resolveClassName("com.example.Foo<com.example.Bar>")
        }
    }
}
