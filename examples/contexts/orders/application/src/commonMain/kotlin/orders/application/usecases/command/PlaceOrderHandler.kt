package com.jimbroze.kbus.example.orders.application.usecases.command

import com.jimbroze.kbus.api.annotations.LoadMessageHandler
import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.domain.event.DomainEventPublisher
import com.jimbroze.kbus.example.orders.application.OrderRepository
import com.jimbroze.kbus.example.orders.application.PaymentGateway
import com.jimbroze.kbus.example.orders.application.StockReservations
import com.jimbroze.kbus.example.orders.contracts.OrderId
import com.jimbroze.kbus.example.orders.contracts.PlaceOrder
import com.jimbroze.kbus.example.orders.contracts.PlaceOrderFailure
import com.jimbroze.kbus.example.orders.contracts.PlaceOrderResult
import com.jimbroze.kbus.example.orders.domain.Order
import com.jimbroze.kbus.example.orders.domain.OrderItem
import com.jimbroze.kbus.example.orders.domain.OrderStatus

@LoadMessageHandler
@Suppress("unused")
class PlaceOrderHandler(
    private val orderRepository: OrderRepository,
    private val paymentGateway: PaymentGateway,
    private val stockReservations: StockReservations,
    private val domainEventPublisher: DomainEventPublisher,
) : CommandHandler<PlaceOrder, PlaceOrderResult>() {
    override suspend fun handle(message: PlaceOrder): PlaceOrderResult {
        val items = message.lines.map { OrderItem(it.productId, it.quantity, it.unitPrice) }
        val total = items.sumOf { it.quantity * it.unitPrice }

        val itemWithNoStock =
            items.firstOrNull { !stockReservations.reserve(it.productId, it.quantity) }

        return when {
            itemWithNoStock != null ->
                BusResult.failure(PlaceOrderFailure.OutOfStock(itemWithNoStock.productId))
            !paymentGateway.charge(message.customerId, total, message.paymentMethodId) ->
                BusResult.failure(PlaceOrderFailure.PaymentDeclined(message.customerId))
            else -> BusResult.success(OrderId(confirm(message.customerId, items, total).id))
        }
    }

    private suspend fun confirm(customerId: String, items: List<OrderItem>, total: Double): Order {
        val order =
            Order(
                id = "order-$customerId-${items.size}",
                customerId = customerId,
                items = items,
                total = total,
                status = OrderStatus.CONFIRMED,
                domainEventPublisher = domainEventPublisher,
            )

        order.place()
        return orderRepository.save(order)
    }
}
