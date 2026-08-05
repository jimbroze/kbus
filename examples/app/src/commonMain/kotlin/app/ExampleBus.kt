package com.jimbroze.kbus.example.app

import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.core.infrastructure.inbox.InMemoryInboxStore
import com.jimbroze.kbus.core.infrastructure.outbox.InMemoryOutboxStore
import com.jimbroze.kbus.core.middleware.middleware.AutoPublishIntegrationEvents
import com.jimbroze.kbus.core.middleware.middleware.autoPublish
import com.jimbroze.kbus.core.module.ContextConfig
import com.jimbroze.kbus.core.module.inbox.ContextInbox
import com.jimbroze.kbus.core.module.inbox.InboxAckPolicy
import com.jimbroze.kbus.core.registry.generation.subscribe
import com.jimbroze.kbus.core.registry.generation.subscribeDomain
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.example.inventory.application.usecases.event.integration.NotifyWarehouseHandler
import com.jimbroze.kbus.example.inventory.contracts.StockReserved
import com.jimbroze.kbus.example.orders.application.usecases.event.mappings.OrderPlacedMapper
import com.jimbroze.kbus.example.orders.application.usecases.event.integration.RecordStockReservedHandler
import com.jimbroze.kbus.example.orders.application.usecases.event.domain.SendOrderConfirmationEmailHandler
import com.jimbroze.kbus.example.orders.domain.OrderPlaced
import com.jimbroze.kbus.generated.CompileTimeLoadedMessageBus
import com.jimbroze.kbus.generated.loaded
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope

/**
 * Every wiring decision the contexts deliberately left open: which adapter implements a port, who
 * subscribes to what, and whether delivery is durable.
 *
 * Each context supplies its own inbox store, so a context that cannot keep up — or keeps failing —
 * stops consuming without touching the other's progress.
 */
fun exampleBus(
    transactionManager: TransactionManager,
    appScope: CoroutineScope,
): CompileTimeLoadedMessageBus {
    lateinit var bus: CompileTimeLoadedMessageBus
    val container = ExampleContainer { bus }

    bus =
        CompileTimeLoadedMessageBus(
            container,
            transactionManager,
            listOf(AutoPublishIntegrationEvents(autoPublish<OrderPlaced>(OrderPlacedMapper))),
            appScope = appScope,
            outbox = OutboxConfig(store = InMemoryOutboxStore(), pollInterval = 10.seconds),
            orders =
                ContextConfig(
                    inbox = ContextInbox(InMemoryInboxStore(), InboxAckPolicy.HonourEventStrategy),
                    subscriptions =
                        listOf(
                            subscribeDomain(
                                OrderPlaced::class,
                                SendOrderConfirmationEmailHandler::class.loaded,
                            ),
                            subscribe(
                                StockReserved::class,
                                RecordStockReservedHandler::class.loaded,
                            ),
                        ),
                ),
            inventory =
                ContextConfig(
                    inbox = ContextInbox(InMemoryInboxStore(), InboxAckPolicy.HonourEventStrategy),
                    subscriptions =
                        listOf(
                            subscribe(StockReserved::class, NotifyWarehouseHandler::class.loaded)
                        ),
                ),
        )

    return bus
}
