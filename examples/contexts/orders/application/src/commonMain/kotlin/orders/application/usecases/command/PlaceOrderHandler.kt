package com.jimbroze.kbus.example.orders.application.usecases.command

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.GenericFailure
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.domain.event.DomainEventPublisher
import com.jimbroze.kbus.example.orders.application.OrderRepository
import com.jimbroze.kbus.example.orders.application.PaymentGateway
import com.jimbroze.kbus.example.orders.application.StockReservations
import com.jimbroze.kbus.example.orders.contracts.OrderId
import com.jimbroze.kbus.example.orders.contracts.PlaceOrder
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
) : CommandHandler<PlaceOrder, BusResult<OrderId, MessageFailure>>() {
    override suspend fun handle(message: PlaceOrder): BusResult<OrderId, MessageFailure> {
        val items = message.lines.map { OrderItem(it.productId, it.quantity, it.unitPrice) }
        val total = items.sumOf { it.quantity * it.unitPrice }

        val itemWithNoStock =
            items.firstOrNull { !stockReservations.reserve(it.productId, it.quantity) }

        return when {
            itemWithNoStock != null -> failure("No stock for product ${itemWithNoStock.productId}")
            !paymentGateway.charge(message.customerId, total, message.paymentMethodId) ->
                failure("Payment failed")
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

    private fun failure(description: String) =
        BusResult.failure(
            object : MessageFailure {
                override val reason = GenericFailure(description)
            }
        )
}
