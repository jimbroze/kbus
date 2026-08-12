package com.jimbroze.kbus.example.app.manual

import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.infrastructure.inbox.InMemoryInboxStore
import com.jimbroze.kbus.core.infrastructure.outbox.InMemoryOutboxStore
import com.jimbroze.kbus.core.middleware.AutoPublishIntegrationEvents
import com.jimbroze.kbus.core.middleware.autoPublish
import com.jimbroze.kbus.core.module.BoundedContext
import com.jimbroze.kbus.core.module.BoundedContextId
import com.jimbroze.kbus.core.module.domainSubscription
import com.jimbroze.kbus.core.module.inbox.BoundedContextInbox
import com.jimbroze.kbus.core.module.inbox.InboxAckPolicy
import com.jimbroze.kbus.core.module.integrationSubscription
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.persisting.store.QueryHandlerFactory
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.example.inventory.application.usecases.command.ReserveStockHandler
import com.jimbroze.kbus.example.inventory.application.usecases.event.integration.NotifyWarehouseHandler
import com.jimbroze.kbus.example.inventory.application.usecases.query.GetStockLevelHandler
import com.jimbroze.kbus.example.inventory.contracts.GetStockLevel
import com.jimbroze.kbus.example.inventory.contracts.ReserveStock
import com.jimbroze.kbus.example.inventory.contracts.StockReserved
import com.jimbroze.kbus.example.orders.application.usecases.command.CancelAndReplaceOrderHandler
import com.jimbroze.kbus.example.orders.application.usecases.command.PlaceOrderHandler
import com.jimbroze.kbus.example.orders.application.usecases.event.domain.SendOrderConfirmationEmailHandler
import com.jimbroze.kbus.example.orders.application.usecases.event.integration.RecordStockReservedHandler
import com.jimbroze.kbus.example.orders.application.usecases.event.mappings.OrderPlacedMapper
import com.jimbroze.kbus.example.orders.application.usecases.query.GetOrderByIdHandler
import com.jimbroze.kbus.example.orders.contracts.CancelAndReplaceOrder
import com.jimbroze.kbus.example.orders.contracts.GetOrderById
import com.jimbroze.kbus.example.orders.contracts.PlaceOrder
import com.jimbroze.kbus.example.orders.domain.OrderPlaced
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope

/**
 * The same two contexts the generated wiring assembles, with every registration written out: which
 * handler answers which message, and how each is built from the container's adapters and from the
 * dependencies an invocation supplies.
 *
 * What the generator removes is visible here as the volume of this file — and as the fact that
 * nothing checks a handler was registered for a command until the bus is asked for one.
 */
fun manualExampleBus(transactionManager: TransactionManager, appScope: CoroutineScope): MessageBus {
    lateinit var bus: MessageBus
    val container = ManualContainer { bus }

    bus =
        MessageBus(
            contexts = listOf(ordersContext(container), inventoryContext(container)),
            transactionManager = transactionManager,
            middlewares =
                listOf(AutoPublishIntegrationEvents(autoPublish<OrderPlaced>(OrderPlacedMapper))),
            appScope = appScope,
            outbox = OutboxConfig(store = InMemoryOutboxStore(), pollInterval = 10.seconds),
        )

    return bus
}

private fun ordersContext(container: ManualContainer): BoundedContext {
    val stores = HandlerFactoryStoreCollection()

    stores.commandStore.registerHandlers(
        PlaceOrder::class,
        listOf(
            CommandHandlerFactory(PlaceOrderHandler::class) { commandDependencies ->
                PlaceOrderHandler(
                    container.orderRepository,
                    container.paymentGateway,
                    container.stockReservations,
                    commandDependencies.domainEventPublisher,
                )
            }
        ),
    )
    stores.commandStore.registerHandlers(
        CancelAndReplaceOrder::class,
        listOf(
            CommandHandlerFactory(CancelAndReplaceOrderHandler::class) { commandDependencies ->
                CancelAndReplaceOrderHandler(
                    ManualOrdersCommands(commandDependencies.commandExecutor)
                )
            }
        ),
    )
    stores.queryStore.registerHandlers(
        GetOrderById::class,
        listOf(
            QueryHandlerFactory(GetOrderByIdHandler::class) {
                GetOrderByIdHandler(container.orderRepository)
            }
        ),
    )
    stores.eventStore.registerHandlers(
        OrderPlaced::class,
        listOf(
            EventHandlerFactory(SendOrderConfirmationEmailHandler::class) {
                SendOrderConfirmationEmailHandler(container.emailService)
            }
        ),
    )
    stores.eventStore.registerHandlers(
        StockReserved::class,
        listOf(
            EventHandlerFactory(RecordStockReservedHandler::class) { RecordStockReservedHandler() }
        ),
    )

    return BoundedContext(
        id = BoundedContextId("orders"),
        handlerLocator = PersistingHandlerLocator(stores),
        inbox = BoundedContextInbox(InMemoryInboxStore(), InboxAckPolicy.HonourEventStrategy),
        domainSubscriptions =
            listOf(
                domainSubscription(OrderPlaced::class, SendOrderConfirmationEmailHandler::class)
            ),
        integrationSubscriptions =
            listOf(integrationSubscription(StockReserved::class, RecordStockReservedHandler::class)),
    )
}

private fun inventoryContext(container: ManualContainer): BoundedContext {
    val stores = HandlerFactoryStoreCollection()

    stores.commandStore.registerHandlers(
        ReserveStock::class,
        listOf(
            CommandHandlerFactory(ReserveStockHandler::class) { commandDependencies ->
                ReserveStockHandler(
                    container.inventoryRepository,
                    container.stockValidator,
                    commandDependencies.integrationEventPublisher,
                )
            }
        ),
    )
    stores.queryStore.registerHandlers(
        GetStockLevel::class,
        listOf(
            QueryHandlerFactory(GetStockLevelHandler::class) {
                GetStockLevelHandler(container.inventoryRepository)
            }
        ),
    )
    stores.eventStore.registerHandlers(
        StockReserved::class,
        listOf(
            EventHandlerFactory(NotifyWarehouseHandler::class) {
                NotifyWarehouseHandler(container.warehouseNotifier)
            }
        ),
    )

    return BoundedContext(
        id = BoundedContextId("inventory"),
        handlerLocator = PersistingHandlerLocator(stores),
        inbox = BoundedContextInbox(InMemoryInboxStore(), InboxAckPolicy.HonourEventStrategy),
        integrationSubscriptions =
            listOf(integrationSubscription(StockReserved::class, NotifyWarehouseHandler::class)),
    )
}
