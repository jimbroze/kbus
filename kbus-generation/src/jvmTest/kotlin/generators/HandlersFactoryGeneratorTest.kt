package com.jimbroze.kbus.generation.generators

import com.jimbroze.kbus.contracts.annotations.index.RequiredDependencies
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependency
import com.jimbroze.kbus.generation.processing.dependencies.ContextCommandsDependency
import com.jimbroze.kbus.generation.processing.handlers.CommandHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerKind
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

    private fun eventHandler(eventName: String, kind: EventHandlerKind) =
        EventHandlerDefinition(
            HandlerData(
                ClassName("com.example", "${eventName}Handler"),
                ClassName("com.example", eventName),
                UNIT,
                emptyList(),
                "",
            ),
            kind,
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

    /**
     * The accessor is the property's own name, not one derived from its type — `commandExecutor`
     * holds a `NestedCommandExecutor`, so a type-derived name would reference nothing.
     */
    @Test
    fun aCommandScopedDependencyIsReadFromThePropertyHoldingIt() {
        generator.generateClasses(
            setOf(
                CommandHandlerDefinition(
                    HandlerData(
                        ClassName("com.example", "NestForeignCommandHandler"),
                        ClassName("com.example", "NestForeignCommand"),
                        UNIT,
                        listOf(
                            CommandDependency(
                                ClassName(
                                    "com.jimbroze.kbus.core.messages.command",
                                    "NestedCommandExecutor",
                                ),
                                "commandExecutor",
                                RequiredDependencies.COMMAND,
                            )
                        ),
                        "",
                    )
                )
            ),
            emptyList(),
        )

        assertContains(
            generated["DefaultHandlerFactory"],
            "NestForeignCommandHandler(commandDependencies.commandExecutor)",
        )
    }

    @Test
    fun aHandlerAskingForItsContextsCommandsIsHandedThemAsAParameter() {
        generator.generateClasses(setOf(handlerAskingForOrdersCommands()), emptyList())

        assertContains(
            generated["OrdersHandlerFactory"],
            "override fun placeOrderHandler(commandDependencies: CommandDependencies, " +
                "ordersCommands: OrdersCommands): PlaceOrderHandler = " +
                "PlaceOrderHandler(ordersCommands)",
        )
    }

    @Test
    fun theNestedLookupBuildsTheContextsCommandsFromTheDependenciesItWasHanded() {
        generator.generateClasses(setOf(handlerAskingForOrdersCommands()), emptyList())

        assertContains(
            generated["OrdersHandlerFactory"],
            "is PlaceOrder -> this.placeOrderHandler(commandDependencies, " +
                "OrdersCommandExecutor(commandDependencies.commandExecutor))",
        )
    }

    private fun handlerAskingForOrdersCommands() =
        CommandHandlerDefinition(
            HandlerData(
                ClassName("com.example", "PlaceOrderHandler"),
                ClassName("com.example", "PlaceOrder"),
                UNIT,
                listOf(ContextCommandsDependency(ClassName(PACKAGE_PATH, "OrdersCommands"))),
                "orders",
            )
        )

    @Test
    fun aHandlerAskingForTheWholeCommandDependenciesObjectIsGivenItUnqualified() {
        generator.generateClasses(
            setOf(
                CommandHandlerDefinition(
                    HandlerData(
                        ClassName("com.example", "WholeDepsCommandHandler"),
                        ClassName("com.example", "WholeDepsCommand"),
                        UNIT,
                        listOf(
                            CommandDependency(
                                ClassName(
                                    "com.jimbroze.kbus.core.messages.command",
                                    "CommandDependencies",
                                ),
                                CommandDependency.WHOLE_OBJECT,
                                RequiredDependencies.COMMAND,
                            )
                        ),
                        "",
                    )
                )
            ),
            emptyList(),
        )

        assertContains(
            generated["DefaultHandlerFactory"],
            "WholeDepsCommandHandler(commandDependencies)",
        )
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

    @Test
    fun aDomainEventHandlerIsOnlyReachableThroughTheDomainLookup() {
        generator.generateClasses(
            setOf(
                eventHandler("OrderPlaced", EventHandlerKind.DOMAIN),
                eventHandler("OrderShipped", EventHandlerKind.INTEGRATION),
            ),
            emptyList(),
        )

        val factory = generated["DefaultHandlerFactory"]
        val domainLookup = factory.substringAfter("fun <TEvent : DomainEvent> domainEventHandler")
        val integrationLookup =
            factory.substringAfter("fun <TEvent : Event> eventHandler").substringBefore("fun ")

        assertContains(domainLookup, "OrderPlacedHandler::class")
        assertFalse(domainLookup.contains("OrderShippedHandler::class"))
        assertContains(integrationLookup, "OrderShippedHandler::class")
        assertFalse(integrationLookup.contains("OrderPlacedHandler::class"))
    }

    @Test
    fun theDomainLookupReturnsTheDomainHandlerKind() {
        generator.generateClasses(
            setOf(eventHandler("OrderPlaced", EventHandlerKind.DOMAIN)),
            emptyList(),
        )

        val factory = generated["DefaultHandlerFactory"]
        assertContains(factory, "handlerClass: KClass<DomainEventHandler<TEvent>>")
        assertContains(factory, "handlerDependencies: HandlerDependencies")
        assertContains(factory, "): DomainEventHandler<TEvent>?")
    }
}
