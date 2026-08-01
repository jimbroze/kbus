package com.jimbroze.kbus.generation.generators

import com.jimbroze.kbus.generation.processing.handlers.CommandHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.HandlerData
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.UNIT
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextCommandsGeneratorTest {
    private val generated = GeneratedSources()

    private val generator =
        ContextCommandsGenerator(
            generated,
            SilentLogger,
            commandsInterfaceName = "Commands",
            executorClassName = "CommandExecutor",
            packagePath = "com.jimbroze.kbus.generated",
        )

    private fun commandHandler(commandName: String, module: String) =
        CommandHandlerDefinition(
            HandlerData(
                ClassName("com.example", "${commandName}Handler"),
                ClassName("com.example", commandName),
                UNIT,
                emptyList(),
                module,
            )
        )

    private val handlers =
        setOf(commandHandler("PlaceOrder", "orders"), commandHandler("ReserveStock", "inventory"))

    @Test
    fun anInterfaceIsGeneratedPerContextThatOwnsCommands() {
        generator.generateInterfaces(handlers, emptyList())

        assertEquals(setOf("OrdersCommands", "InventoryCommands"), generated.fileNames)
    }

    @Test
    fun anInterfaceNamesOnlyItsOwnContextsCommands() {
        generator.generateInterfaces(handlers, emptyList())

        val ordersCommands = generated["OrdersCommands"]
        assertContains(ordersCommands, "public suspend fun placeOrder(command: PlaceOrder)")
        assertFalse(ordersCommands.contains("reserveStock"), ordersCommands)
    }

    @Test
    fun anInterfaceCarriesTheIdentityOfTheContextItCovers() {
        generator.generateInterfaces(handlers, emptyList())

        assertContains(
            generated["OrdersCommands"],
            """@ContextCommandsFor(contextIdentity = "orders")""",
        )
    }

    @Test
    fun anExecutorImplementsEveryVisibleInterfaceForItsOwnContext() {
        generator.generateExecutors(
            handlers,
            mapOf(
                "orders" to listOf(ClassName("com.jimbroze.kbus.generated", "OrdersCommandsSub")),
                "inventory" to
                    listOf(ClassName("com.jimbroze.kbus.generated", "InventoryCommandsSub")),
            ),
            emptyList(),
        )

        val ordersExecutor = generated["OrdersCommandExecutor"]
        assertTrue(ordersExecutor.contains("OrdersCommandsSub"), ordersExecutor)
        assertFalse(ordersExecutor.contains("InventoryCommandsSub"), ordersExecutor)
    }

    @Test
    fun anExecutorDelegatesEachCommandToTheUntypedExecutor() {
        generator.generateExecutors(handlers, emptyMap(), emptyList())

        assertContains(
            generated["OrdersCommandExecutor"],
            "override suspend fun placeOrder(command: PlaceOrder): Unit = commandExecutor.execute(command)",
        )
    }
}
