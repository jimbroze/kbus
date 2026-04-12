package com.jimbroze.kbus.generation.processing.dependencies

import com.jimbroze.kbus.generation.processing.ConflictPolicy
import com.squareup.kotlinpoet.ClassName
import kotlin.test.Test
import kotlin.test.assertIs

class DependencyConflictPolicyTest {
    private fun createDependencyWithChildren(typeName: ClassName): DependencyWithChildren {
        return DependencyWithChildren(
            metadata = PropertyDependency(typeName),
            topLevelDependencies = emptyList(),
            cannotBeAutoloaded = false,
        )
    }

    @Test
    fun new_dependency_with_no_existing_returns_accept() {
        val dep = createDependencyWithChildren(ClassName("com.example", "Foo"))

        val result = DependencyConflictPolicy.evaluate(dep, emptyList())

        assertIs<ConflictPolicy.Result.Accept>(result)
    }

    @Test
    fun exact_duplicate_returns_exact_duplicate() {
        val dep = createDependencyWithChildren(ClassName("com.example", "Foo"))
        val existing = createDependencyWithChildren(ClassName("com.example", "Foo"))

        val result = DependencyConflictPolicy.evaluate(dep, listOf(existing))

        assertIs<ConflictPolicy.Result.ExactDuplicate>(result)
    }

    @Test
    fun conflicting_name_different_dependency_returns_invalid_conflict() {
        // Same simple name "Foo" but different packages → same generated name, different dependency
        val dep = createDependencyWithChildren(ClassName("com.other", "Foo"))
        val existing = createDependencyWithChildren(ClassName("com.example", "Foo"))

        val result = DependencyConflictPolicy.evaluate(dep, listOf(existing))

        assertIs<ConflictPolicy.Result.InvalidConflict>(result)
    }

    @Test
    fun different_name_returns_accept() {
        val dep = createDependencyWithChildren(ClassName("com.example", "Foo"))
        val existing = createDependencyWithChildren(ClassName("com.example", "Bar"))

        val result = DependencyConflictPolicy.evaluate(dep, listOf(existing))

        assertIs<ConflictPolicy.Result.Accept>(result)
    }
}
