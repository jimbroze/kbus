package com.jimbroze.kbus.api.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BusResultTest {

    private sealed interface OrderFailure : MessageFailure {
        data class OutOfStock(val productId: String) : OrderFailure

        data class PaymentDeclined(val customerId: String) : OrderFailure
    }

    @Test
    fun `reports success when it holds a value`() {
        val result = BusResult.success("value")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `does not report failure when it holds a value`() {
        val result = BusResult.success("value")

        assertFalse(result.isFailure)
    }

    @Test
    fun `exposes the value it holds`() {
        val result = BusResult.success("hello")

        assertEquals("hello", result.getOrNull())
    }

    @Test
    fun `exposes no failure when it holds a value`() {
        val result = BusResult.success("value")

        assertNull(result.failureOrNull())
    }

    @Test
    fun `treats a null value as a success`() {
        val result = BusResult.success(null)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `reports failure when it holds a failure`() {
        val result = BusResult.failure(OrderFailure.OutOfStock("widget"))

        assertTrue(result.isFailure)
    }

    @Test
    fun `does not report success when it holds a failure`() {
        val result = BusResult.failure(OrderFailure.OutOfStock("widget"))

        assertFalse(result.isSuccess)
    }

    @Test
    fun `exposes no value when it holds a failure`() {
        val result = BusResult.failure(OrderFailure.OutOfStock("widget"))

        assertNull(result.getOrNull())
    }

    @Test
    fun `exposes the failure it holds`() {
        val failure = OrderFailure.OutOfStock("widget")
        val result = BusResult.failure(failure)

        assertEquals(failure, result.failureOrNull())
    }

    @Test
    fun `renders the value it holds in its string form`() {
        val result = BusResult.success(42)

        assertEquals("Success(42)", result.toString())
    }

    @Test
    fun `renders the failure it holds in its string form`() {
        val result = BusResult.failure(OrderFailure.OutOfStock("widget"))

        assertEquals("Failure(OutOfStock(productId=widget))", result.toString())
    }

    @Test
    fun `applies the transform to the value when mapping a success`() {
        val result = BusResult.success(2).mapSuccess { it * 3 }

        assertEquals(6, result.getOrNull())
    }

    @Test
    fun `leaves the failure untouched when mapping the success of a failure`() {
        val failure = OrderFailure.OutOfStock("widget")
        val result = BusResult.failure(failure).mapSuccess { "mapped" }

        assertEquals(failure, result.failureOrNull())
    }

    @Test
    fun `applies the transform to the failure when mapping a failure`() {
        val mapped = OrderFailure.PaymentDeclined("customer-1")
        val result = BusResult.failure(OrderFailure.OutOfStock("widget")).mapFailure { mapped }

        assertEquals(mapped, result.failureOrNull())
    }

    @Test
    fun `leaves the value untouched when mapping the failure of a success`() {
        val result =
            BusResult.success("value").mapFailure { OrderFailure.PaymentDeclined("customer-1") }

        assertEquals("value", result.getOrNull())
    }

    @Test
    fun `collapses a success through the success branch`() {
        val result = BusResult.success(2)

        assertEquals("value 2", result.collapse({ "value $it" }, { "failed" }))
    }

    @Test
    fun `collapses a failure through the failure branch`() {
        val result = BusResult.failure(OrderFailure.OutOfStock("widget"))

        assertEquals(
            "failed widget",
            result.collapse({ "value $it" }, { "failed ${it.productId}" }),
        )
    }
}
