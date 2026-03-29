package com.jimbroze.kbus.generation.test.orders.application.usecases.command

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.GenericFailure
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.contracts.uow.ExecuteInTransaction
import com.jimbroze.kbus.domain.event.DomainEventPublisher
import com.jimbroze.kbus.generation.test.orders.application.OrderRepository
import com.jimbroze.kbus.generation.test.orders.application.PaymentGateway
import com.jimbroze.kbus.generation.test.orders.domain.Order
import com.jimbroze.kbus.generation.test.orders.domain.OrderItem
import com.jimbroze.kbus.generation.test.orders.domain.OrderStatus

class PlaceOrder(val customerId: String, val items: List<OrderItem>, val paymentMethodId: String) :
    Command<BusResult<Order, MessageFailure>>()

// TODO improve example model
@LoadMessageHandler
@Suppress("unused")
class PlaceOrderHandler(
    private val orderRepository: OrderRepository,
    private val paymentGateway: PaymentGateway,
    private val domainEventPublisher: DomainEventPublisher,
) :
    CommandHandler<PlaceOrder, BusResult<Order, MessageFailure>>(),
    ExecuteInTransaction<PlaceOrder, BusResult<Order, MessageFailure>> {
    override suspend fun handle(message: PlaceOrder): BusResult<Order, MessageFailure> {
        val total = message.items.sumOf { it.quantity * it.unitPrice }

        val paymentSucceeded =
            paymentGateway.charge(message.customerId, total, message.paymentMethodId)
        if (!paymentSucceeded) {
            return BusResult.failure(
                object : MessageFailure {
                    override val reason = GenericFailure("Payment failed")
                }
            )
        }

        val order =
            Order(
                id = "order-${message.customerId}-${message.items.size}",
                customerId = message.customerId,
                items = message.items,
                total = total,
                status = OrderStatus.CONFIRMED,
                domainEventPublisher = domainEventPublisher,
            )

        order.place()
        orderRepository.save(order)

        return BusResult.success(order)
    }
}
