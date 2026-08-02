package com.jimbroze.kbus.generation.generators

import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.core.bus.BaseMessageBus
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.module.inbox.InboxConfig
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.generation.processing.handlers.CommandHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.HandlerData
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.UNIT
import kotlin.test.Test
import kotlin.test.assertContains

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
                busSuperClass = BaseMessageBus::class,
                middlewareClass = Middleware::class,
                transactionManagerClass = TransactionManager::class,
                outboxConfigClass = OutboxConfig::class,
                inboxConfigClass = InboxConfig::class,
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
    fun noContextIsReachableFromTheBuiltBus() {
        val bus = generateBus()

        assertContains(bus, "private val contexts: Contexts")
    }
}
