package com.jimbroze.kbus.generation.generators

import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.core.bus.BaseMessageBus
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.module.inbox.InboxTuning
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.generation.processing.handlers.CommandHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.HandlerData
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.UNIT
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BusGeneratorContextsTest {
    private val generated = GeneratedSources()

    private val generator =
        BusGenerator(
            generated,
            SilentLogger,
            BusConfig(
                busClassName = "CompileTimeLoadedMessageBus",
                dependenciesInterfaceName = "AllDependencies",
                handlerFactoryName = "HandlerFactory",
                contextClassName = "Context",
                busSuperClass = BaseMessageBus::class,
                middlewareClass = Middleware::class,
                transactionManagerClass = TransactionManager::class,
                outboxConfigClass = OutboxConfig::class,
                inboxTuningClass = InboxTuning::class,
            ),
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

    private fun generateBus(): String {
        generator.generateClass(
            setOf(commandHandler("PlaceOrder", "orders"), commandHandler("SendEmail", "")),
            emptyList(),
        )
        return generated["CompileTimeLoadedMessageBus"]
    }

    @Test
    fun eachContextIsBuiltFromTheConfigPassedUnderItsOwnName() {
        val bus = generateBus()

        assertContains(
            bus,
            "BoundedContext(BoundedContextId(\"orders\"), ordersLocator, " +
                "ordersConfig.inbox, ordersConfig.subscriptions)",
        )
        assertContains(
            bus,
            "BoundedContext(BoundedContextId.DEFAULT, defaultLocator, " +
                "defaultConfig.inbox, defaultConfig.subscriptions)",
        )
    }

    @Test
    fun theBusTakesOneContextConfigParameterPerContext() {
        val bus = generateBus()

        assertContains(bus, "default: ContextConfig = ContextConfig()")
        assertContains(bus, "orders: ContextConfig = ContextConfig()")
    }

    @Test
    fun aCommandRunsAgainstItsOwnContext() {
        val bus = generateBus()

        assertContains(
            bus,
            "commandExecutor.execute(command, boundedContexts.orders, handlerCreator)",
        )
        assertContains(
            bus,
            "commandExecutor.execute(command, boundedContexts.default, handlerCreator)",
        )
    }

    @Test
    fun aHandlerIsBuiltByTheFactoryOfTheContextTheCommandRunsAgainst() {
        val bus = generateBus()

        assertContains(bus, "boundedContexts.orders.handlerFactory.placeOrderHandler(")
        assertContains(bus, "boundedContexts.default.handlerFactory.sendEmailHandler(")
        assertFalse(bus.contains("private val ordersHandlerFactory: OrdersHandlerFactory ="))
    }

    @Test
    fun everyTypedDispatchFunctionRefusesToRunOnAnUnstartedBus() {
        val bus = generateBus()

        assertEquals(
            2,
            Regex("""public suspend fun \w+\(\w+: \w+\) \{\n\s*checkStarted\(\)""")
                .findAll(bus)
                .count(),
        )
    }

    @Test
    fun everyContextIsRegisteredOnTheBusThatBuiltIt() {
        val bus = generateBus()

        assertContains(bus, "buildContexts = { builder -> CompileTimeLoadedMessageBus.Contexts(")
        assertContains(bus, "public val orders: OrdersContext =")
        assertContains(bus, "builder.register(BoundedContext(BoundedContextId(\"orders\")")
        assertContains(bus, "public val default: DefaultContext =")
        assertContains(bus, "builder.register(BoundedContext(BoundedContextId.DEFAULT")
    }

    @Test
    fun eachContextGetsItsOwnTypeDelegatingToTheContextItRegistered() {
        val bus = generateBus()

        assertContains(
            bus,
            "public class OrdersContext(\n" +
                "  registeredContext: CommandOwningContext<NestedCommandExecutor>,\n" +
                "  public val handlerFactory: OrdersHandlerFactory,\n" +
                ") : CommandOwningContext<NestedCommandExecutor> by registeredContext",
        )
        assertContains(bus, "public class DefaultContext(")
    }

    @Test
    fun noContextIsReachableFromTheBuiltBus() {
        val bus = generateBus()

        assertFalse(bus.contains("val contexts: Contexts"))
        assertContains(bus, "public class Contexts internal constructor(")
    }
}
