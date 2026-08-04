package com.jimbroze.kbus.example.app

import com.jimbroze.kbus.core.uow.EmptyTransactionManager
import com.jimbroze.kbus.example.inventory.application.usecases.event.NotifyWarehouseHandler
import com.jimbroze.kbus.example.inventory.contracts.GetStockLevel
import com.jimbroze.kbus.example.orders.application.usecases.event.RecordStockReservedHandler
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

private val oneBook = listOf(OrderLine("book", 1, 9.99))

/**
 * An outbox and two inboxes are background work, which no constructor starts. The wait lets the
 * pollers settle into their first sleep so a later advance measures dispatch, not startup.
 */
private suspend fun TestScope.startedBus(appScope: CoroutineScope) =
    exampleBus(EmptyTransactionManager(), appScope).also {
        it.start()
        advanceVirtualTime(50)
    }

@OptIn(ExperimentalCoroutinesApi::class)
class ExampleAppTest {
    @Test
    fun test_a_command_returns_only_the_identifier_of_what_it_created() = runTest {
        val bus = startedBus(backgroundScope)

        val orderId = bus.execute(PlaceOrder("customer-1", oneBook, "card")).getOrNull()

        assertNotNull(orderId, "the order was placed")
        val summary = bus.fetch(GetOrderById(orderId.value)).getOrNull()
        assertEquals("customer-1", assertNotNull(summary).customerId)
    }

    @Test
    fun test_placing_an_order_reserves_stock_in_the_inventory_context() = runTest {
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
    fun test_an_event_one_context_publishes_reaches_every_subscribing_context() = runTest {
        val bus = startedBus(backgroundScope)
        val recordedBefore = RecordStockReservedHandler.timesHandled

        bus.execute(PlaceOrder("customer-3", oneBook, "card"))
        advanceVirtualTime(300)

        assertEquals(recordedBefore + 1, RecordStockReservedHandler.timesHandled)
    }

    @Test
    fun test_a_command_reached_through_its_contexts_typed_executor_shares_the_invocation() =
        runTest {
            val bus = startedBus(backgroundScope)

            val orderId = bus.execute(CancelAndReplaceOrder("customer-4", oneBook)).getOrNull()

            assertNotNull(orderId, "the nested PlaceOrder ran inside CancelAndReplaceOrder")
        }

    @Test
    fun test_a_query_answers_from_the_context_that_owns_it() = runTest {
        val bus = startedBus(backgroundScope)

        val stock = bus.fetch(GetStockLevel("book")).getOrNull()

        assertEquals(100, assertNotNull(stock).available)
    }
}
