package com.jimbroze.kbus.example.app

import com.jimbroze.kbus.example.adapters.ExampleDatabase
import com.jimbroze.kbus.example.adapters.ExampleDatabaseTransactionManager
import com.jimbroze.kbus.example.orders.contracts.PlaceOrder
import com.jimbroze.kbus.generated.PlaceOrderGateway
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest

class ExampleAppTest : ExampleAppContract() {
    override fun createBus(appScope: CoroutineScope) =
        exampleBus(ExampleDatabaseTransactionManager(ExampleDatabase()), appScope)

    @Test
    fun `sends a command through the generated gateway for it`() = runTest {
        val bus = createBus(backgroundScope).also { it.start() }

        val orderId =
            PlaceOrderGateway(bus).execute(PlaceOrder("customer-5", oneBook, "card")).getOrNull()

        assertNotNull(orderId, "the gateway reached the handler the generator found for it")
    }
}
