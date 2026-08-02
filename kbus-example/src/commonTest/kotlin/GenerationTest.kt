@file:OptIn(ExperimentalTime::class)

package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.contracts.common.MissingHandlerException
import com.jimbroze.kbus.core.bus.BaseMessageBus
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.infrastructure.inbox.InMemoryInboxStore
import com.jimbroze.kbus.core.infrastructure.lock.inMemoryAtomicLock
import com.jimbroze.kbus.core.infrastructure.outbox.InMemoryOutboxStore
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.middleware.middleware.AutoPublishIntegrationEvents
import com.jimbroze.kbus.core.middleware.middleware.LockingMiddleware
import com.jimbroze.kbus.core.module.ContextConfig
import com.jimbroze.kbus.core.module.inbox.ContextInbox
import com.jimbroze.kbus.core.module.inbox.InboxAckPolicy
import com.jimbroze.kbus.core.module.inbox.InboxConfig
import com.jimbroze.kbus.core.registry.generation.subscribe
import com.jimbroze.kbus.core.registry.generation.subscribeDomain
import com.jimbroze.kbus.core.uow.EmptyTransactionManager
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.generated.AutoLoader
import com.jimbroze.kbus.generated.CompileTimeLoadedMessageBus
import com.jimbroze.kbus.generated.generatedAutoPublishRegistrations
import com.jimbroze.kbus.generated.loaded
import com.jimbroze.kbus.generation.test.inventory.application.usecases.command.ReserveStock
import com.jimbroze.kbus.generation.test.inventory.application.usecases.event.NotifyWarehouseHandler
import com.jimbroze.kbus.generation.test.inventory.application.usecases.event.StockReserved
import com.jimbroze.kbus.generation.test.inventory.application.usecases.query.GetStockLevel
import com.jimbroze.kbus.generation.test.inventory.infrastructure.ExampleWarehouseNotifier
import com.jimbroze.kbus.generation.test.inventory.infrastructure.InMemoryInventoryRepository
import com.jimbroze.kbus.generation.test.orders.application.EmailService
import com.jimbroze.kbus.generation.test.orders.application.usecases.command.PlaceOrder
import com.jimbroze.kbus.generation.test.orders.application.usecases.command.PlaceOrderForRegularCustomer
import com.jimbroze.kbus.generation.test.orders.application.usecases.event.HandleOrderPlacedIntegrationHandler
import com.jimbroze.kbus.generation.test.orders.application.usecases.event.SendOrderConfirmationEmailHandler
import com.jimbroze.kbus.generation.test.orders.application.usecases.query.GetOrderById
import com.jimbroze.kbus.generation.test.orders.domain.OrderItem
import com.jimbroze.kbus.generation.test.orders.domain.OrderPlaced
import com.jimbroze.kbus.generation.test.orders.infrastructure.ExamplePaymentGateway
import com.jimbroze.kbus.generation.test.orders.infrastructure.InMemoryOrderRepository
import com.jimbroze.kbus.testdoubles.AutoTickingClock
import com.jimbroze.kbus.testdoubles.advanceVirtualTime
import com.test.external.ExternalEmpty
import com.test.external.ExternalNestedWithExternal
import com.test.external.ExternalNestedWithPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class RecordingEmailService : EmailService {
    val confirmedOrderIds = mutableListOf<String>()

    override suspend fun sendOrderConfirmation(orderId: String, customerId: String) {
        confirmedOrderIds.add(orderId)
    }
}

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
    val recordingEmailService = RecordingEmailService()
    override val emailService: EmailService = recordingEmailService
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

@OptIn(ExperimentalCoroutinesApi::class)
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
                default =
                    ContextConfig(
                        subscriptions =
                            listOf(
                                subscribeDomain(
                                    TestGeneratorEvent::class,
                                    TestGeneratorEventHandler::class.loaded,
                                )
                            )
                    ),
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
                    default =
                        ContextConfig(
                            subscriptions =
                                listOf(
                                    subscribe(
                                        TestShipmentIntegration::class,
                                        TestShipmentIntegrationHandler::class.loaded,
                                    )
                                )
                        ),
                )

            val handledBefore = TestShipmentIntegrationHandler.timesHandled
            bus.execute(TestShipmentCommand())
            assertEquals(handledBefore + 1, TestShipmentIntegrationHandler.timesHandled)
        }

    @Test
    fun test_a_handler_typed_calls_a_sibling_command_in_its_own_context() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                emptyList(),
                appScope = backgroundScope,
            )

        val result =
            bus.execute(
                PlaceOrderForRegularCustomer("customer-1", listOf(OrderItem("book", 1, 9.99)))
            )

        assertEquals("customer-1", result.getOrNull()!!.customerId)
    }

    @Test
    fun test_a_submodule_commands_domain_event_reaches_its_own_contexts_domain_handler() = runTest {
        val dependencies = Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope)
        val bus =
            CompileTimeLoadedMessageBus(
                dependencies,
                EmptyTransactionManager(),
                emptyList(),
                appScope = backgroundScope,
                orders =
                    ContextConfig(
                        subscriptions =
                            listOf(
                                subscribeDomain(
                                    OrderPlaced::class,
                                    SendOrderConfirmationEmailHandler::class.loaded,
                                )
                            )
                    ),
            )

        val order =
            bus.execute(PlaceOrder("customer-1", listOf(OrderItem("book", 1, 9.99)), "card"))
        // The handler dispatches after the transaction, so it outlives the command's return.
        advanceVirtualTime(100)

        assertEquals(
            listOf(order.getOrNull()!!.id),
            dependencies.recordingEmailService.confirmedOrderIds,
        )
    }

    @Test
    fun test_it_fetches_a_query_each_submodule_context_owns() = runTest {
        val dependencies = Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope)
        val bus =
            CompileTimeLoadedMessageBus(
                dependencies,
                EmptyTransactionManager(),
                emptyList(),
                appScope = backgroundScope,
            )

        val order =
            bus.execute(PlaceOrder("customer-1", listOf(OrderItem("book", 1, 9.99)), "card"))
        bus.execute(ReserveStock("product-1", 1))

        assertEquals(
            "customer-1",
            bus.fetch(GetOrderById(order.getOrNull()!!.id)).getOrNull()!!.customerId,
        )
        assertNotNull(bus.fetch(GetStockLevel("product-1")).getOrNull())
    }

    @Test
    fun test_a_command_another_context_owns_is_not_nestable() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                emptyList(),
                appScope = backgroundScope,
            )

        assertFailsWith<MissingHandlerException> { bus.execute(NestForeignCommand()) }
    }

    @Test
    fun test_an_integration_event_a_command_published_reaches_the_outbox() = runTest {
        val outboxStore = InMemoryOutboxStore()
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                emptyList(),
                appScope = backgroundScope,
                outbox = OutboxConfig(store = outboxStore, pollInterval = 10.seconds),
                inventory =
                    ContextConfig(
                        subscriptions =
                            listOf(
                                subscribe(
                                    StockReserved::class,
                                    NotifyWarehouseHandler::class.loaded,
                                )
                            )
                    ),
            )
        bus.start()
        // Let the poller's immediate first (empty) pass settle into its long sleep.
        advanceVirtualTime(50)

        val handledBefore = NotifyWarehouseHandler.timesHandled
        bus.execute(ReserveStock("product-1", 1))
        advanceVirtualTime(150)

        assertEquals(handledBefore + 1, NotifyWarehouseHandler.timesHandled)
        assertTrue(
            outboxStore.fetchUnpublished(10).isEmpty(),
            "the outbox drained what the command published",
        )
    }

    /**
     * `opportunisticDispatch = false` so the assertion pins the inbox's own durable step: the
     * envelope is saved and left for the pump, not dispatched inline on the routing path.
     */
    @Test
    fun test_an_inboxed_context_saves_what_it_is_routed_before_dispatching_it() = runTest {
        val inboxStore = InMemoryInboxStore()
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                emptyList(),
                appScope = backgroundScope,
                outbox = OutboxConfig(store = InMemoryOutboxStore(), pollInterval = 10.seconds),
                inbox = InboxConfig(opportunisticDispatch = false, pollInterval = 50.milliseconds),
                inventory =
                    ContextConfig(
                        inbox = ContextInbox(inboxStore, InboxAckPolicy.HonourEventStrategy),
                        subscriptions =
                            listOf(
                                subscribe(
                                    StockReserved::class,
                                    NotifyWarehouseHandler::class.loaded,
                                )
                            ),
                    ),
            )
        bus.start()
        advanceVirtualTime(50)

        val handledBefore = NotifyWarehouseHandler.timesHandled
        bus.execute(ReserveStock("product-1", 1))
        advanceVirtualTime(300)

        assertEquals(handledBefore + 1, NotifyWarehouseHandler.timesHandled)
        assertTrue(
            inboxStore.fetchPending(10).isEmpty(),
            "the inbox pump consumed and acked the envelope",
        )
    }

    /**
     * `opportunisticDrain = false` leaves the poller as the only thing that delivers, so a command
     * executed after `stop` isolates whether the background work is still running.
     */
    @Test
    fun test_a_stopped_bus_no_longer_polls_its_outbox() = runTest {
        val outboxStore = InMemoryOutboxStore()
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                emptyList(),
                appScope = backgroundScope,
                outbox =
                    OutboxConfig(
                        store = outboxStore,
                        pollInterval = 50.milliseconds,
                        opportunisticDrain = false,
                    ),
                inventory =
                    ContextConfig(
                        subscriptions =
                            listOf(
                                subscribe(
                                    StockReserved::class,
                                    NotifyWarehouseHandler::class.loaded,
                                )
                            )
                    ),
            )
        bus.start()

        val handledBefore = NotifyWarehouseHandler.timesHandled
        bus.execute(ReserveStock("product-1", 1))
        advanceVirtualTime(200)
        assertEquals(handledBefore + 1, NotifyWarehouseHandler.timesHandled)

        bus.stop(1.seconds)
        val handledAtStop = NotifyWarehouseHandler.timesHandled
        bus.execute(ReserveStock("product-2", 1))
        advanceVirtualTime(500)

        assertEquals(
            handledAtStop,
            NotifyWarehouseHandler.timesHandled,
            "no poller is still draining after stop",
        )
        assertTrue(outboxStore.fetchUnpublished(10).isNotEmpty(), "the event is still durable")
    }

    @Test
    fun test_it_observes_an_integration_event_a_command_published() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                emptyList(),
                appScope = backgroundScope,
            )

        val observed = async { bus.observe<StockReserved>().first() }
        runCurrent()

        bus.execute(ReserveStock("product-1", 1))

        assertEquals("product-1", observed.await().productId)
    }

    @Test
    fun test_each_context_only_dispatches_its_own_integration_handlers() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                listOf(AutoPublishIntegrationEvents(generatedAutoPublishRegistrations)),
                appScope = backgroundScope,
                orders = ContextConfig(subscriptions = orderSubscriptions),
                inventory = ContextConfig(subscriptions = inventorySubscriptions),
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
