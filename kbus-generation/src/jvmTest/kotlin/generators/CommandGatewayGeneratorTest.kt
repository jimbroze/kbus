package com.jimbroze.kbus.generation.generators

import com.jimbroze.kbus.generation.processing.handlers.CommandHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerKind
import com.jimbroze.kbus.generation.processing.handlers.HandlerData
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.UNIT
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CommandGatewayGeneratorTest {
    private val generated = GeneratedSources()

    private val generator =
        CommandGatewayGenerator(
            generated,
            SilentLogger,
            gatewayClassSuffix = "Gateway",
            packagePath = "com.jimbroze.kbus.generated",
        )

    private fun commandHandler(commandName: String) =
        CommandHandlerDefinition(
            HandlerData(
                ClassName("com.example", "${commandName}Handler"),
                ClassName("com.example", commandName),
                ClassName("com.example", "${commandName}Result"),
                emptyList(),
                "inventory",
            )
        )

    @Test
    fun aGatewayIsGeneratedPerCommandThatHasAHandler() {
        generator.generateGateways(
            setOf(commandHandler("ReserveStock"), commandHandler("PlaceOrder")),
            emptyList(),
        )

        assertEquals(setOf("ReserveStockGateway", "PlaceOrderGateway"), generated.fileNames)
    }

    @Test
    fun aGatewayIsTypedToItsOwnCommandAndResult() {
        generator.generateGateways(setOf(commandHandler("ReserveStock")), emptyList())

        assertContains(
            generated["ReserveStockGateway"],
            "CommandGateway<ReserveStock, ReserveStockResult>",
        )
    }

    @Test
    fun aGatewayDelegatesToTheBus() {
        generator.generateGateways(setOf(commandHandler("ReserveStock")), emptyList())

        assertContains(
            generated["ReserveStockGateway"],
            "override suspend fun execute(command: ReserveStock): ReserveStockResult = " +
                "bus.execute(command)",
        )
    }

    @Test
    fun noGatewayIsGeneratedForAMessageThatIsNotACommand() {
        generator.generateGateways(
            setOf(
                EventHandlerDefinition(
                    HandlerData(
                        ClassName("com.example", "StockReservedHandler"),
                        ClassName("com.example", "StockReserved"),
                        UNIT,
                        emptyList(),
                        "orders",
                    ),
                    EventHandlerKind.INTEGRATION,
                )
            ),
            emptyList(),
        )

        assertEquals(emptySet(), generated.fileNames)
    }
}
