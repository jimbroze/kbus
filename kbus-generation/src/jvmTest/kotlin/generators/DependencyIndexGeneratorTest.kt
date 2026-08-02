package com.jimbroze.kbus.generation.generators

import com.squareup.kotlinpoet.ClassName
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class DependencyIndexGeneratorTest {
    private val generated = GeneratedSources()

    private val generator =
        DependencyIndexGenerator(
            generated,
            SilentLogger,
            indexClassName = "DependenciesIndex",
            packagePath = "com.jimbroze.kbus.index",
        )

    private fun generateIndex(contextCommandInterfaces: Map<String, ClassName>) {
        generator.generateIndexClass(
            emptySet(),
            emptySet(),
            emptySet(),
            contextCommandInterfaces,
            emptyList(),
        )
    }

    @Test
    fun anIndexNamesTheCommandInterfaceGeneratedForEachContext() {
        generateIndex(
            mapOf("orders" to ClassName("com.jimbroze.kbus.generated.sub", "OrdersCommands"))
        )

        assertContains(
            generated["DependenciesIndex"],
            """ContextCommandsInfo(contextIdentity = "orders", """ +
                """interfaceClass = "com.jimbroze.kbus.generated.sub.OrdersCommands")""",
        )
    }

    @Test
    fun anIndexDeclaringNoCommandInterfacesOmitsTheArgument() {
        generateIndex(emptyMap())

        val index = generated["DependenciesIndex"]
        assertFalse(index.contains("contextCommands"), index)
    }
}
