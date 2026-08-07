package com.jimbroze.kbus.example.app

import com.jimbroze.kbus.core.bus.BaseMessageBus
import com.jimbroze.kbus.example.inventory.application.usecases.event.integration.NotifyWarehouseHandler
import com.jimbroze.kbus.example.inventory.contracts.GetStockLevel
import com.jimbroze.kbus.example.orders.application.usecases.event.integration.RecordStockReservedHandler
import com.jimbroze.kbus.example.orders.contracts.CancelAndReplaceOrder
import com.jimbroze.kbus.example.orders.contracts.GetOrderById
import com.jimbroze.kbus.example.orders.contracts.OrderLine
import com.jimbroze.kbus.example.orders.contracts.PlaceOrder
import com.jimbroze.kbus.testdoubles.advanceVirtualTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * The requirements the example contexts must meet however their bus is wired. A wiring states its
 * own name for these by subclassing, and adds whatever only it can promise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ExampleAppContract {
    protected val oneBook = listOf(OrderLine("book", 1, 9.99))

    /** Must return a bus over both example contexts, not yet started. */
    abstract fun createBus(appScope: CoroutineScope): BaseMessageBus<*>

    /**
     * An outbox and two inboxes are background work, which no constructor starts. The wait lets the
     * pollers settle into their first sleep so a later advance measures dispatch, not startup.
     */
    protected suspend fun TestScope.startedBus(appScope: CoroutineScope) =
        createBus(appScope).also {
            it.start()
            advanceVirtualTime(50)
        }

    @Test
    fun `returns only the identifier of what a command created`() = runTest {
        val bus = startedBus(backgroundScope)

        val orderId = bus.execute(PlaceOrder("customer-1", oneBook, "card")).getOrNull()

        assertNotNull(orderId, "the order was placed")
        val summary = bus.fetch(GetOrderById(orderId.value)).getOrNull()
        assertEquals("customer-1", assertNotNull(summary).customerId)
    }

    @Test
    fun `reserves stock in the inventory context when an order is placed`() = runTest {
        val bus = startedBus(backgroundScope)
        val notifiedBefore = NotifyWarehouseHandler.timesHandled

        bus.execute(PlaceOrder("customer-2", oneBook, "card"))
        advanceVirtualTime(300)

        assertEquals(
            notifiedBefore + 1,
            NotifyWarehouseHandler.timesHandled,
            "the reservation the anti-corruption layer sent reached inventory's own handler",
        )
    }

    @Test
    fun `delivers an event one context publishes to every subscribing context`() = runTest {
        val bus = startedBus(backgroundScope)
        val recordedBefore = RecordStockReservedHandler.timesHandled

        bus.execute(PlaceOrder("customer-3", oneBook, "card"))
        advanceVirtualTime(300)

        assertEquals(recordedBefore + 1, RecordStockReservedHandler.timesHandled)
    }

    @Test
    fun `shares the invocation with a command reached through its context's nested executor`() =
        runTest {
            val bus = startedBus(backgroundScope)

            val orderId = bus.execute(CancelAndReplaceOrder("customer-4", oneBook)).getOrNull()

            assertNotNull(orderId, "the nested PlaceOrder ran inside CancelAndReplaceOrder")
        }

    @Test
    fun `answers a query from the context that owns it`() = runTest {
        val bus = startedBus(backgroundScope)

        val stock = bus.fetch(GetStockLevel("book")).getOrNull()

        assertEquals(100, assertNotNull(stock).available)
    }
}
