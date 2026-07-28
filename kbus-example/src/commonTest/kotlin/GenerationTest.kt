@file:OptIn(ExperimentalTime::class)

package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.core.bus.BaseMessageBus
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.infrastructure.lock.inMemoryAtomicLock
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.middleware.middleware.AutoPublishIntegrationEvents
import com.jimbroze.kbus.core.middleware.middleware.LockingMiddleware
import com.jimbroze.kbus.core.uow.EmptyTransactionManager
import com.jimbroze.kbus.generated.AutoLoader
import com.jimbroze.kbus.generated.CompileTimeLoadedMessageBus
import com.jimbroze.kbus.generated.generatedAutoPublishRegistrations
import com.jimbroze.kbus.generated.loaded
import com.jimbroze.kbus.generation.test.inventory.application.usecases.command.ReserveStock
import com.jimbroze.kbus.generation.test.inventory.application.usecases.event.NotifyWarehouseHandler
import com.jimbroze.kbus.generation.test.inventory.application.usecases.event.StockReserved
import com.jimbroze.kbus.generation.test.inventory.infrastructure.ExampleWarehouseNotifier
import com.jimbroze.kbus.generation.test.inventory.infrastructure.InMemoryInventoryRepository
import com.jimbroze.kbus.generation.test.orders.application.usecases.event.HandleOrderPlacedIntegrationHandler
import com.jimbroze.kbus.generation.test.orders.application.usecases.event.OrderPlacedIntegration
import com.jimbroze.kbus.generation.test.orders.domain.OrderPlaced
import com.jimbroze.kbus.generation.test.orders.infrastructure.ExampleEmailService
import com.jimbroze.kbus.generation.test.orders.infrastructure.ExamplePaymentGateway
import com.jimbroze.kbus.generation.test.orders.infrastructure.InMemoryOrderRepository
import com.jimbroze.kbus.testdoubles.AutoTickingClock
import com.jimbroze.kbus.testdoubles.advanceVirtualTime
import com.test.external.ExternalEmpty
import com.test.external.ExternalNestedWithExternal
import com.test.external.ExternalNestedWithPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest

// TODO don't add any dependencies not in module???
class Dependencies(private val instant: Instant, applicationScope: CoroutineScope) : AutoLoader() {
    override val lockingMiddleware by lazy {
        LockingMiddleware(
            { scope: CoroutineScope -> inMemoryAtomicLock(scope) },
            5.seconds,
            30.seconds,
        )
    }
    override val messageBus = MessageBus(appScope = applicationScope)

    override val anObject: AnObject = AnObject

    override val genericClassOfString: GenericClass<String> = GenericClass("a string")
    override val genericClassOfListOfString: GenericClass<List<String>> =
        GenericClass(listOf("a string in a list"))

    override val typeAliasStringOne = "hello, "
    override val typeAliasStringTwo = "hello again"

    override val clock: Clock = FixedClock(instant)
    val tickingClock: Clock = AutoTickingClock(instant)
    override val baseMessageBus: BaseMessageBus = messageBus

    override val containsString = ContainsString("a string")
    override val containsFunction = ContainsFunction { a, b -> a + b }
    override val typeAliasStringCombiner: TypeAliasStringCombiner = { a, b -> a + b }
    override val externalEmpty = ExternalEmpty()
    override val externalInterface = externalEmpty
    override val externalNestedWithPrimitive = ExternalNestedWithPrimitive("A string")
    override val externalNestedWithExternal = ExternalNestedWithExternal(externalEmpty)
    override val orderRepository = InMemoryOrderRepository()
    override val paymentGateway = ExamplePaymentGateway()
    override val emailService = ExampleEmailService()
    override val inventoryRepository = InMemoryInventoryRepository()
    override val warehouseNotifier = ExampleWarehouseNotifier()

    override val transientExample: TransientExample
        get() = FixedClock(tickingClock.now())

    override val lazySingletonExample: LazySingletonExample by lazy {
        FixedClock(tickingClock.now())
    }
    override val eagerSingletonExample: EagerSingletonExample = FixedClock(tickingClock.now())

    override fun requiresCommandDepsContainsPrimitive(commandDependencies: CommandDependencies) =
        RequiresCommandDepsContainsPrimitive(
            this.requiresCommandDepsContainsInterface(commandDependencies),
            instant,
        )
}

class GenerationTest {
    @Test
    fun test_it_executes_commands() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                emptyList(),
            )

        assertEquals("success", bus.execute(NestedClassesCommand("")).getOrNull())
        assertEquals("success", bus.execute(OtherClassesCommand("")).getOrNull())
        assertEquals("success", bus.execute(GenericClassCommand("")).getOrNull())
        assertEquals("success", bus.execute(InterfacesCommand("")).getOrNull())
        assertEquals("success", bus.execute(NonClassTypesCommand("")).getOrNull())
        assertEquals("success", bus.execute(ExternalDependenciesCommand("")).getOrNull())
        assertEquals("success", bus.execute(ExternalDependenciesCommandSub("")).getOrNull())
    }

    @Test
    fun test_it_handles_lifecycles() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                emptyList(),
            )

        val firstResult = bus.execute(LifeCycleTestCommand()).getOrNull()
        assertNotNull(firstResult)

        val secondResult = bus.execute(LifeCycleTestCommand()).getOrNull()
        assertNotNull(secondResult)

        assertTrue(secondResult.transientTime > firstResult.transientTime)
        assertEquals(secondResult.lazySingletonTime, firstResult.lazySingletonTime)
        assertEquals(secondResult.eagerSingletonTime, firstResult.eagerSingletonTime)

        assertTrue(firstResult.lazySingletonTime > firstResult.eagerSingletonTime)
        assertTrue(secondResult.lazySingletonTime > secondResult.eagerSingletonTime)
    }

    @Test
    fun test_it_fetches_queries() = runTest {
        val instant = Instant.parse("2024-02-23T19:01:09Z")

        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(instant, backgroundScope),
                EmptyTransactionManager(),
                emptyList(),
            )

        val result = bus.fetch(TestGeneratorQuery("The time is ", "now "))

        assertEquals("The time is now 2024-02-23T19:01:09Z", result.getOrNull())
    }

    @Test
    fun test_it_dispatches_events() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                emptyList(),
            )

        bus.domainEventMapper.addDomainHandlers(
            TestGeneratorEvent::class,
            listOf(TestGeneratorEventHandler::class.loaded),
        )

        val handledBefore = TestGeneratorEventHandler.timesHandled
        bus.execute(TestEventPublishingCommand())
        assertEquals(handledBefore + 1, TestGeneratorEventHandler.timesHandled)
    }

    @Test
    fun test_generated_auto_publish_registrations_only_contain_opted_in_events() {
        // TestShipmentIntegration (direct AutoPublishesFrom) and TestShipmentAnalytics (indirect,
        // via a generic intermediate interface) both opt in; TestShipmentAudit has no
        // AutoPublishesFrom companion, so it contributes no registration.
        val registrationsForShipmentEvent =
            generatedAutoPublishRegistrations.count { it.eventClass == TestShipmentEvent::class }

        assertEquals(2, registrationsForShipmentEvent)
    }

    @Test
    fun test_generated_auto_publish_registrations_propagate_from_submodules() {
        val eventClasses = generatedAutoPublishRegistrations.map { it.eventClass }

        assertTrue(eventClasses.contains(OrderPlaced::class))
    }

    /**
     * `kbus-example` is a root module with its own handlers and no `kbus.boundedContextIdentity`,
     * so its integration handlers land in the default context. This is the regression guard for the
     * unassigned-identity path.
     */
    @Test
    fun test_root_module_handlers_without_a_bounded_context_identity_land_in_the_default_context() =
        runTest {
            val bus =
                CompileTimeLoadedMessageBus(
                    Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                    EmptyTransactionManager(),
                    listOf(AutoPublishIntegrationEvents(generatedAutoPublishRegistrations)),
                )

            bus.default.addEventHandlers(
                TestShipmentIntegration::class,
                listOf(TestShipmentIntegrationHandler::class.loaded),
            )

            val handledBefore = TestShipmentIntegrationHandler.timesHandled
            bus.execute(TestShipmentCommand())
            assertEquals(handledBefore + 1, TestShipmentIntegrationHandler.timesHandled)
        }

    @Test
    fun test_each_context_only_dispatches_its_own_integration_handlers() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                listOf(AutoPublishIntegrationEvents(generatedAutoPublishRegistrations)),
                appScope = backgroundScope,
            )

        // Each submodule declares its own kbus.boundedContextIdentity, so the generated bus
        // exposes one registration point per bounded context instead of a single ambiguous mapper.
        bus.orders.addEventHandlers(
            OrderPlacedIntegration::class,
            listOf(HandleOrderPlacedIntegrationHandler::class.loaded),
        )
        bus.inventory.addEventHandlers(
            StockReserved::class,
            listOf(NotifyWarehouseHandler::class.loaded),
        )

        val ordersHandledBefore = HandleOrderPlacedIntegrationHandler.timesHandled
        val inventoryHandledBefore = NotifyWarehouseHandler.timesHandled

        bus.execute(ReserveStock("product-1", 1))
        // Integration dispatch is fire-and-forget, so give it a moment to land.
        advanceVirtualTime(100)

        // Only the inventory context has a handler for StockReserved; orders is untouched.
        assertEquals(inventoryHandledBefore + 1, NotifyWarehouseHandler.timesHandled)
        assertEquals(ordersHandledBefore, HandleOrderPlacedIntegrationHandler.timesHandled)
    }
}
