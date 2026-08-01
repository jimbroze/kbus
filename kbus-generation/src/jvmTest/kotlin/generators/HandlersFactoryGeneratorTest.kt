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

private const val PACKAGE_PATH = "com.jimbroze.kbus.generated"

class HandlersFactoryGeneratorTest {
    private val generated = GeneratedSources()

    private val generator =
        HandlersFactoryGenerator(
            generated,
            SilentLogger,
            factoryClassName = "HandlerFactory",
            dependenciesInterfaceName = "AllDependencies",
            handlersInterfaceName = "Handlers",
            commandExecutorClassName = "CommandExecutor",
            packagePath = PACKAGE_PATH,
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

    @Test
    fun oneFactoryIsGeneratedPerBoundedContext() {
        generator.generateClasses(
            setOf(
                commandHandler("PlaceOrder", "orders"),
                commandHandler("ReserveStock", "inventory"),
                commandHandler("SendEmail", ""),
            ),
            emptyList(),
        )

        assertEquals(
            setOf("DefaultHandlerFactory", "InventoryHandlerFactory", "OrdersHandlerFactory"),
            generated.fileNames,
        )
    }

    @Test
    fun theDefaultContextsFactoryIsGeneratedEvenWithNoHandlersOfItsOwn() {
        generator.generateClasses(setOf(commandHandler("PlaceOrder", "orders")), emptyList())

        assertContains(generated.fileNames, "DefaultHandlerFactory")
    }

    @Test
    fun aContextsFactoryHoldsOnlyItsOwnCommands() {
        generator.generateClasses(
            setOf(
                commandHandler("PlaceOrder", "orders"),
                commandHandler("ReserveStock", "inventory"),
            ),
            emptyList(),
        )

        val ordersFactory = generated["OrdersHandlerFactory"]
        assertTrue(ordersFactory.contains("PlaceOrder"), ordersFactory)
        assertFalse(ordersFactory.contains("ReserveStock"), ordersFactory)
    }

    @Test
    fun aContextsCommandTypesAreItsOwnCommandsAlone() {
        generator.generateClasses(
            setOf(
                commandHandler("PlaceOrder", "orders"),
                commandHandler("ReserveStock", "inventory"),
            ),
            emptyList(),
        )

        assertContains(
            generated["InventoryHandlerFactory"],
            "override fun commandTypes(): Set<KClass<out Command<*>>> = setOf(ReserveStock::class)",
        )
    }
}
