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
    fun `accepts a dependency nothing is registered under`() {
        val dep = createDependencyWithChildren(ClassName("com.example", "Foo"))

        val result = DependencyConflictPolicy.evaluate(dep, emptyList())

        assertIs<ConflictPolicy.Result.Accept>(result)
    }

    @Test
    fun `reports an identical dependency as an exact duplicate`() {
        val dep = createDependencyWithChildren(ClassName("com.example", "Foo"))
        val existing = createDependencyWithChildren(ClassName("com.example", "Foo"))

        val result = DependencyConflictPolicy.evaluate(dep, listOf(existing))

        assertIs<ConflictPolicy.Result.ExactDuplicate>(result)
    }

    @Test
    fun `reports a different dependency under a taken name as a conflict`() {
        // Same simple name "Foo" but different packages → same generated name, different dependency
        val dep = createDependencyWithChildren(ClassName("com.other", "Foo"))
        val existing = createDependencyWithChildren(ClassName("com.example", "Foo"))

        val result = DependencyConflictPolicy.evaluate(dep, listOf(existing))

        assertIs<ConflictPolicy.Result.InvalidConflict>(result)
    }

    @Test
    fun `accepts a dependency under a name of its own`() {
        val dep = createDependencyWithChildren(ClassName("com.example", "Foo"))
        val existing = createDependencyWithChildren(ClassName("com.example", "Bar"))

        val result = DependencyConflictPolicy.evaluate(dep, listOf(existing))

        assertIs<ConflictPolicy.Result.Accept>(result)
    }
}
