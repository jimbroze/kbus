package com.jimbroze.kbus.generation.generators

import com.jimbroze.kbus.core.boundedcontext.inbox.InboxTuning
import com.jimbroze.kbus.core.bus.BaseMessageBus
import com.jimbroze.kbus.core.middleware.infrastructure.Middleware
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.generation.processing.handlers.CommandHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.HandlerData
import com.jimbroze.kbus.generation.processing.handlers.QueryHandlerDefinition
import com.jimbroze.kbus.infrastructure.transaction.TransactionManager
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
                commandExecutorClassName = "CommandExecutor",
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

    private fun queryHandler(queryName: String, module: String) =
        QueryHandlerDefinition.create(
            HandlerData(
                ClassName("com.example", "${queryName}Handler"),
                ClassName("com.example", queryName),
                UNIT,
                emptyList(),
                module,
            ),
            SilentLogger,
            null,
        )!!

    private fun generateBus(): String {
        generator.generateClass(
            setOf(commandHandler("PlaceOrder", "orders"), commandHandler("SendEmail", "")),
            emptyList(),
        )
        return generated["CompileTimeLoadedMessageBus"]
    }

    @Test
    fun `builds each context from the config passed under its own name`() {
        val bus = generateBus()

        assertContains(
            bus,
            "BoundedContext(BoundedContextId(\"orders\"), ordersLocator, " +
                "ordersConfig.inbox, ordersConfig.domainSubscriptions, ordersConfig.integrationSubscriptions)",
        )
        assertContains(
            bus,
            "BoundedContext(BoundedContextId.DEFAULT, defaultLocator, " +
                "defaultConfig.inbox, defaultConfig.domainSubscriptions, defaultConfig.integrationSubscriptions)",
        )
    }

    @Test
    fun `gives the bus one bounded context config parameter per context`() {
        val bus = generateBus()

        assertContains(bus, "default: BoundedContextConfig = BoundedContextConfig()")
        assertContains(bus, "orders: BoundedContextConfig = BoundedContextConfig()")
    }

    @Test
    fun `runs a command against its own context`() {
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
    fun `builds a handler with the factory of the context its command runs against`() {
        val bus = generateBus()

        assertContains(bus, "boundedContexts.orders.handlerFactory.placeOrderHandler(")
        assertContains(bus, "boundedContexts.default.handlerFactory.sendEmailHandler(")
        assertFalse(bus.contains("private val ordersHandlerFactory: OrdersHandlerFactory ="))
    }

    @Test
    fun `refuses every typed dispatch on an unstarted bus`() {
        val bus = generateBus()

        assertEquals(
            2,
            Regex("""public suspend fun \w+\(\w+: \w+\) \{\n\s*checkStarted\(\)""")
                .findAll(bus)
                .count(),
        )
    }

    @Test
    fun `registers every context on the bus that built it`() {
        val bus = generateBus()

        assertContains(bus, "buildContexts = { builder -> CompileTimeLoadedMessageBus.Contexts(")
        assertContains(bus, "public val orders: OrdersContext =")
        assertContains(bus, "builder.register(BoundedContext(BoundedContextId(\"orders\")")
        assertContains(bus, "public val default: DefaultContext =")
        assertContains(bus, "builder.register(BoundedContext(BoundedContextId.DEFAULT")
    }

    @Test
    fun `types each context's commands to that context`() {
        val bus = generateBus()

        assertContains(
            bus,
            "public class OrdersContext(\n" +
                "  registeredContext: OwningContext,\n" +
                "  public val handlerFactory: OrdersHandlerFactory,\n" +
                ") : OwningContext by registeredContext,\n" +
                "    CommandOwningContext<OrdersCommandExecutor>",
        )
        assertContains(
            bus,
            "override fun typedCommands(nestedCommandExecutor: NestedCommandExecutor): " +
                "OrdersCommandExecutor = OrdersCommandExecutor(nestedCommandExecutor)",
        )
    }

    @Test
    fun `falls back to the untyped executor for a context owning no commands`() {
        generator.generateClass(setOf(queryHandler("GetOrder", "orders")), emptyList())
        val bus = generated["CompileTimeLoadedMessageBus"]

        assertContains(bus, "CommandOwningContext<NestedCommandExecutor>")
        assertContains(
            bus,
            "override fun typedCommands(nestedCommandExecutor: NestedCommandExecutor): " +
                "NestedCommandExecutor = nestedCommandExecutor",
        )
    }

    @Test
    fun `keeps every context unreachable from the built bus`() {
        val bus = generateBus()

        assertFalse(bus.contains("val contexts: Contexts"))
        assertContains(bus, "public class Contexts internal constructor(")
    }
}
