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
    fun `generates one interface per context that owns commands`() {
        generator.generateInterfaces(handlers, emptyList())

        assertEquals(setOf("OrdersCommands", "InventoryCommands"), generated.fileNames)
    }

    @Test
    fun `names in an interface only its own context's commands`() {
        generator.generateInterfaces(handlers, emptyList())

        val ordersCommands = generated["OrdersCommands"]
        assertContains(ordersCommands, "public suspend fun placeOrder(command: PlaceOrder)")
        assertFalse(ordersCommands.contains("reserveStock"), ordersCommands)
    }

    @Test
    fun `returns each generated interface against the identity of the context it covers`() {
        val interfaces = generator.generateInterfaces(handlers, emptyList())

        assertEquals(
            mapOf(
                "orders" to ClassName("com.jimbroze.kbus.generated", "OrdersCommands"),
                "inventory" to ClassName("com.jimbroze.kbus.generated", "InventoryCommands"),
            ),
            interfaces,
        )
    }

    @Test
    fun `returns an unassigned context's interface against the empty identity`() {
        val interfaces =
            generator.generateInterfaces(setOf(commandHandler("SendEmail", "")), emptyList())

        assertEquals(
            mapOf("" to ClassName("com.jimbroze.kbus.generated", "DefaultCommands")),
            interfaces,
        )
    }

    @Test
    fun `makes an executor implement every visible interface for its own context`() {
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
    fun `makes an executor delegate each command to the untyped executor`() {
        generator.generateExecutors(handlers, emptyMap(), emptyList())

        assertContains(
            generated["OrdersCommandExecutor"],
            "override suspend fun placeOrder(command: PlaceOrder): Unit = commandExecutor.execute(command)",
        )
    }
}
